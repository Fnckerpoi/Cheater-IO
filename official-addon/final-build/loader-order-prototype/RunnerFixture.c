#include <assert.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
int main(int argc,char **argv){
 assert(argc==4);
 void *flutter=dlopen(argv[2],RTLD_NOW|RTLD_GLOBAL);if(!flutter){puts(dlerror());return 2;}
 unsigned (*order)(void)=dlsym(flutter,"FlutterOrder");unsigned (*lookup)(void)=dlsym(flutter,"FirstLookupOrder");unsigned (*observed)(void)=dlsym(flutter,"BootstrapAtFirstLookup");assert(order&&lookup&&observed);
 if(!strcmp(argv[1],"late")){
  void *bootstrap=dlopen(argv[3],RTLD_NOW|RTLD_GLOBAL);assert(bootstrap);
  printf("LATE_DEPENDENCY_RED flutter=%u first_lookup=%u observed_bootstrap=%u\n",order(),lookup(),observed());
  assert(observed()>0); /* Deliberate actual RED: loading bootstrap after Flutter cannot repair first lookup. */
 }else{
  assert(observed()>0 && observed()<order() && order()<lookup());
  printf("STRONG_DEPENDENCY_GREEN bootstrap=%u flutter=%u first_lookup=%u\n",observed(),order(),lookup());
 }
 return 0;
}
