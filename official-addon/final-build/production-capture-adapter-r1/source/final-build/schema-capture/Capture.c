#define _DARWIN_C_SOURCE
#include "Capture.h"
#include "CaptureInternal.h"
#include "SchemaPolicy.h"
#include "../../manuscript-lab/schema-observer/SchemaView.h"
#include "../../manuscript-lab/memory-copy/MemoryCopy.h"
#include <stdatomic.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <limits.h>
#include <stdio.h>
#include <time.h>
static _Atomic(TIOCaptureOpen) Original;
static _Atomic unsigned State=TIOCaptureBlockedEarlyRuntime;
__attribute__((visibility("hidden"))) bool TIOCaptureBindOriginalInternal(TIOCaptureOpen original){
 if(!original)return false;
 TIOCaptureOpen expected=NULL;
 if(!atomic_compare_exchange_strong_explicit(&Original,&expected,original,memory_order_release,memory_order_relaxed))return false;
 atomic_store_explicit(&State,TIOCaptureWaiting,memory_order_release);return true;
}
static _Atomic bool Terminal,Active,StartClaimed;
static int CaptureDirectory=-1;
#if defined(TIO_CAPTURE_TESTING)
static int Home=-1;
#endif
static uint64_t (*Clock)(void);
static uint64_t Started,Last;
#if defined(TIO_CAPTURE_TESTING)
static unsigned Copies;
#endif
static TIOSchemaContext ViewContext;
static bool copy_own(void*u,uintptr_t a,size_t n,void*d,size_t*c){(void)u;
#if defined(TIO_CAPTURE_TESTING)
Copies++;
#endif
return TIOMemoryCopySelf(NULL,a,n,d,c);}
static bool write_all(int fd,const void*b,size_t n){const unsigned char*p=b;while(n){ssize_t got=write(fd,p,n);if(got<0&&errno==EINTR)continue;if(got<=0)return false;p+=got;n-=(size_t)got;}return true;}
#if defined(TIO_CAPTURE_TESTING)
static int directory_at(int parent,const char*name,bool private_mode){if(mkdirat(parent,name,0700)&&errno!=EEXIST)return -1;int fd=openat(parent,name,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return -1;struct stat st;if(fstat(fd,&st)||!S_ISDIR(st.st_mode)||st.st_uid!=getuid()||(private_mode&&(st.st_mode&07777)!=0700)){close(fd);return -1;}return fd;}
#endif
static bool export_schema(const TIOSchemaOwnedBytes*owned){
 int lab=-1;
#if defined(TIO_CAPTURE_TESTING)
 if(Home>=0){int documents=directory_at(Home,"Documents",false);if(documents<0)return false;lab=directory_at(documents,"ManuscriptLab",true);close(documents);}
 else
#endif
 lab=fcntl(CaptureDirectory,F_DUPFD_CLOEXEC,0);
 if(lab<0)return false;
 struct stat directory;if(fstat(lab,&directory)||!S_ISDIR(directory.st_mode)||directory.st_uid!=geteuid()||(directory.st_mode&07777)!=0700){close(lab);return false;}
 const char*leaf="captured_schema_projection.json";
 const char*pending=".captured_schema_projection.pending";
 int fd=openat(lab,pending,O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);if(fd<0){close(lab);return false;}
 struct stat st;bool ok=!fstat(fd,&st)&&S_ISREG(st.st_mode)&&st.st_nlink==1&&(st.st_mode&0777)==0600;
 static const char prefix[]="{\"schema\":";
 static const char suffix[]=",\"actual_manuscript_schema_known\":false,\"observer_policy_supported\":true}";
 if(owned->length>65536-(sizeof(prefix)-1)-(sizeof(suffix)-1))ok=false;
 if(ok)ok=write_all(fd,prefix,sizeof(prefix)-1)&&write_all(fd,owned->bytes,owned->length)&&write_all(fd,suffix,sizeof(suffix)-1)&&fsync(fd)==0;
 if(close(fd))ok=false;
 if(ok)ok=renameatx_np(lab,pending,lab,leaf,RENAME_EXCL)==0;
 /* The final path appears atomically and is never overwritten. */
 if(!ok)unlinkat(lab,pending,0);close(lab);return ok;
}
static uint64_t monotonic_seconds(void){struct timespec t;if(clock_gettime(CLOCK_MONOTONIC,&t)||t.tv_sec<=0)return 0;return(uint64_t)t.tv_sec;}
static void configure_context(uint64_t start){
 Started=Last=start;TIOSchemaContextInit(&ViewContext);ViewContext.enabled=true;
 /* Internal adapter owns identity/entry qualification; these fixed observer fields
  * do not validate it or confer authority to an external caller. */
 ViewContext.caller_verified_lab_identity=true;ViewContext.caller_verified_entry_lifetime=true;ViewContext.caller_verified_arm64_c_abi=true;memcpy(ViewContext.caller_verified_engine_sha,TIO_SCHEMA_ENGINE_SHA,65);ViewContext.caller_verified_open_va=TIO_SCHEMA_OPEN_VA;ViewContext.schema_argument_ordinal=6;ViewContext.armed_at_seconds=start;ViewContext.ttl_seconds=120;ViewContext.event_limit=1;
}
__attribute__((visibility("hidden"))) bool TIOCaptureStartInternal(int trusted_dirfd){
 if(!atomic_load_explicit(&Original,memory_order_acquire)||trusted_dirfd<0)return false;
 int descriptor_flags=fcntl(trusted_dirfd,F_GETFD);struct stat source,st;
 if(descriptor_flags<0||!(descriptor_flags&FD_CLOEXEC)||fstat(trusted_dirfd,&source)||!S_ISDIR(source.st_mode)||source.st_uid!=geteuid()||(source.st_mode&07777)!=0700)return false;
 /* Darwin does not retain open-time O_NOFOLLOW/O_DIRECTORY in F_GETFL.
  * Reopen only the descriptor's own directory; traversal provenance remains
  * the qualified same-module bootstrap's precondition, never fd authority. */
 int fd=openat(trusted_dirfd,".",O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return false;
 if(fstat(fd,&st)||!S_ISDIR(st.st_mode)||st.st_uid!=geteuid()||(st.st_mode&07777)!=0700||st.st_dev!=source.st_dev||st.st_ino!=source.st_ino){close(fd);return false;}
 uint64_t start=monotonic_seconds();if(!start||start>UINT64_MAX-120){close(fd);return false;}
 bool unused=false;if(!atomic_compare_exchange_strong(&StartClaimed,&unused,true)){close(fd);return false;}
 CaptureDirectory=fd;Clock=monotonic_seconds;configure_context(start);
 atomic_store_explicit(&State,TIOCaptureWaiting,memory_order_release);atomic_store_explicit(&Active,true,memory_order_release);return true;
}
static void observe(void*schema,void*key){
 if(key){unsigned waiting=TIOCaptureWaiting;atomic_compare_exchange_strong(&State,&waiting,TIOCaptureKeyRejected);return;}
 bool unused=false;if(!atomic_compare_exchange_strong(&Terminal,&unused,true))return;
 TIOSchemaOwnedBytes owned={0};uint64_t now=Clock();
 if(now<Started||now<Last||now-Started>=120){atomic_store(&State,TIOCaptureExpired);return;}Last=now;
 TIOSchemaStatus copied=TIOSchemaCopyAtOpenEntry(&ViewContext,(uintptr_t)schema,(uintptr_t)key,now,copy_own,NULL,&owned);
 if(copied!=TIOSchemaOK){atomic_store(&State,TIOCaptureInvalidSchema);return;}
 size_t canonical_length=0;unsigned char*canonical=TIOCaptureCanonicalSchema(owned.bytes,owned.length,&canonical_length);
 if(!canonical)atomic_store(&State,TIOCaptureInvalidSchema);
 else {TIOSchemaOwnedBytes projected={canonical,canonical_length};atomic_store(&State,export_schema(&projected)?TIOCaptureWritten:TIOCaptureExportFailed);TIOSchemaOwnedBytesRelease(&projected);}
 TIOSchemaOwnedBytesRelease(&owned);
}
#if defined(TIO_CAPTURE_TESTING)
bool TIOCaptureFixtureBind(TIOCaptureOpen original,const char*owned_home,uint64_t(*clock)(void)){
 if(!original||!owned_home||!clock||atomic_load(&Original))return false;
 /* TESTING-only root: canonical caller-created public fixture directory, not an app credential. */
 char real[PATH_MAX];if(!realpath(owned_home,real)||strcmp(real,owned_home))return false;
 int fd=open(owned_home,O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);if(fd<0)return false;
 struct stat st;if(fstat(fd,&st)||!S_ISDIR(st.st_mode)||st.st_uid!=getuid()||(st.st_mode&07777)!=0700){close(fd);return false;}
 uint64_t start=clock();if(!start||start>UINT64_MAX-120){close(fd);return false;}
 Home=fd;Clock=clock;Started=Last=start;
 TIOSchemaContextInit(&ViewContext);ViewContext.enabled=true;
 /* Explicit host fixture identity only. Excluded entirely from production. */
 ViewContext.caller_verified_lab_identity=true;ViewContext.caller_verified_entry_lifetime=true;ViewContext.caller_verified_arm64_c_abi=true;memcpy(ViewContext.caller_verified_engine_sha,TIO_SCHEMA_ENGINE_SHA,65);ViewContext.caller_verified_open_va=TIO_SCHEMA_OPEN_VA;ViewContext.schema_argument_ordinal=6;ViewContext.armed_at_seconds=start;ViewContext.ttl_seconds=120;ViewContext.event_limit=1;
 if(!TIOCaptureBindOriginalInternal(original))return false;
 atomic_store(&StartClaimed,true);atomic_store_explicit(&Active,true,memory_order_release);return true;
}
unsigned TIOCaptureFixtureCopyCount(void){return Copies;}
#endif
TIOCaptureState TIOCaptureGetState(void){return(TIOCaptureState)atomic_load(&State);}
__attribute__((visibility("hidden"))) uint8_t TIOCaptureForward(void**out,uint32_t id,void*name,void*path,bool sqlite,void*schema,uint32_t max,void*key,uint32_t cf,uint32_t cb,float ratio){
 TIOCaptureOpen original=atomic_load_explicit(&Original,memory_order_acquire);if(!original)abort();
 int incoming=errno;
 if(atomic_load_explicit(&Active,memory_order_acquire))observe(schema,key);
 errno=incoming;return original(out,id,name,path,sqlite,schema,max,key,cf,cb,ratio);
}
