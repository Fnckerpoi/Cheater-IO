#ifndef TIO_STORAGE_IDENTITY_H
#define TIO_STORAGE_IDENTITY_H
#import <Foundation/Foundation.h>

#ifdef TIO_STORAGE_LAB_BUNDLE_ID
#define TIO_STORAGE_IS_LAB 1
#ifndef TIO_TARGET_BUNDLE_ID
#error "Lab storage requires the exact compiled target bundle"
#endif
#define TIO_STORAGE_LAB_PREFIX "com.duriea.turboio.manuscriptlab106."
#define TIO_STORAGE_PREFIX_LENGTH (sizeof(TIO_STORAGE_LAB_PREFIX)-1)
#define TIO_STORAGE_BUNDLE_LENGTH (sizeof(TIO_STORAGE_LAB_BUNDLE_ID)-1)
#define TIO_STORAGE_RUN_AT(I) (TIO_STORAGE_LAB_BUNDLE_ID[TIO_STORAGE_PREFIX_LENGTH+(I)])
#define TIO_STORAGE_RUN_ALNUM(I) ((TIO_STORAGE_RUN_AT(I)>='a'&&TIO_STORAGE_RUN_AT(I)<='z')||(TIO_STORAGE_RUN_AT(I)>='0'&&TIO_STORAGE_RUN_AT(I)<='9'))
#define TIO_STORAGE_RUN_CHAR(I) ((TIO_STORAGE_PREFIX_LENGTH+(I)>=TIO_STORAGE_BUNDLE_LENGTH)||TIO_STORAGE_RUN_ALNUM(I)||TIO_STORAGE_RUN_AT(I)=='-')
_Static_assert(sizeof(TIO_STORAGE_LAB_BUNDLE_ID)==__builtin_strlen(TIO_STORAGE_LAB_BUNDLE_ID)+1,"Lab storage bundle must be a NUL-free literal");
_Static_assert(__builtin_strcmp(TIO_STORAGE_LAB_BUNDLE_ID,TIO_TARGET_BUNDLE_ID)==0,"Lab storage and target bundle must match exactly");
_Static_assert(__builtin_strncmp(TIO_STORAGE_LAB_BUNDLE_ID,TIO_STORAGE_LAB_PREFIX,TIO_STORAGE_PREFIX_LENGTH)==0 && TIO_STORAGE_BUNDLE_LENGTH>=TIO_STORAGE_PREFIX_LENGTH+6 && TIO_STORAGE_BUNDLE_LENGTH<=TIO_STORAGE_PREFIX_LENGTH+40,"Storage isolation requires an exact lab run bundle");
_Static_assert(TIO_STORAGE_RUN_ALNUM(0) && __builtin_strcmp(&TIO_STORAGE_LAB_BUNDLE_ID[TIO_STORAGE_PREFIX_LENGTH],"research")!=0 && __builtin_strcmp(&TIO_STORAGE_LAB_BUNDLE_ID[TIO_STORAGE_PREFIX_LENGTH],"official")!=0,"Invalid or reserved storage lab run ID");
_Static_assert(TIO_STORAGE_RUN_CHAR(0) && TIO_STORAGE_RUN_CHAR(1) && TIO_STORAGE_RUN_CHAR(2) && TIO_STORAGE_RUN_CHAR(3) && TIO_STORAGE_RUN_CHAR(4) && TIO_STORAGE_RUN_CHAR(5) && TIO_STORAGE_RUN_CHAR(6) && TIO_STORAGE_RUN_CHAR(7) && TIO_STORAGE_RUN_CHAR(8) && TIO_STORAGE_RUN_CHAR(9) && TIO_STORAGE_RUN_CHAR(10) && TIO_STORAGE_RUN_CHAR(11) && TIO_STORAGE_RUN_CHAR(12) && TIO_STORAGE_RUN_CHAR(13) && TIO_STORAGE_RUN_CHAR(14) && TIO_STORAGE_RUN_CHAR(15) && TIO_STORAGE_RUN_CHAR(16) && TIO_STORAGE_RUN_CHAR(17) && TIO_STORAGE_RUN_CHAR(18) && TIO_STORAGE_RUN_CHAR(19) && TIO_STORAGE_RUN_CHAR(20) && TIO_STORAGE_RUN_CHAR(21) && TIO_STORAGE_RUN_CHAR(22) && TIO_STORAGE_RUN_CHAR(23) && TIO_STORAGE_RUN_CHAR(24) && TIO_STORAGE_RUN_CHAR(25) && TIO_STORAGE_RUN_CHAR(26) && TIO_STORAGE_RUN_CHAR(27) && TIO_STORAGE_RUN_CHAR(28) && TIO_STORAGE_RUN_CHAR(29) && TIO_STORAGE_RUN_CHAR(30) && TIO_STORAGE_RUN_CHAR(31) && TIO_STORAGE_RUN_CHAR(32) && TIO_STORAGE_RUN_CHAR(33) && TIO_STORAGE_RUN_CHAR(34) && TIO_STORAGE_RUN_CHAR(35) && TIO_STORAGE_RUN_CHAR(36) && TIO_STORAGE_RUN_CHAR(37) && TIO_STORAGE_RUN_CHAR(38) && TIO_STORAGE_RUN_CHAR(39),"Invalid storage lab run alphabet");
#define TIO_STORAGE_ADDON_LITERAL TIO_STORAGE_LAB_BUNDLE_ID ".private-addon"
#define TIO_STORAGE_PROFILE_LITERAL TIO_STORAGE_LAB_BUNDLE_ID ".profile"
#define TIO_STORAGE_KNOWLEDGE_LITERAL TIO_STORAGE_LAB_BUNDLE_ID ".knowledge"
#define TIO_STORAGE_PREVIEW_LITERAL TIO_STORAGE_LAB_BUNDLE_ID ".preview"
#define TIO_STORAGE_NAVIGATION_LITERAL TIO_STORAGE_LAB_BUNDLE_ID ".navigation"
#else
#define TIO_STORAGE_IS_LAB 0
#ifdef TIO_TARGET_BUNDLE_ID
_Static_assert(__builtin_strncmp(TIO_TARGET_BUNDLE_ID,"com.duriea.turboio.manuscriptlab106.",sizeof("com.duriea.turboio.manuscriptlab106.")-1)!=0,"Lab target requires explicit isolated storage identity");
#endif
#define TIO_STORAGE_ADDON_LITERAL "io.turboio.official-private-addon"
#define TIO_STORAGE_PROFILE_LITERAL "io.turboio.official-profile"
#define TIO_STORAGE_KNOWLEDGE_LITERAL "io.turboio.official-knowledge"
#define TIO_STORAGE_PREVIEW_LITERAL "io.turboio.research.preview"
#define TIO_STORAGE_NAVIGATION_LITERAL "io.turboio.navigation"
#endif

static inline NSString *TIOStorageAddonDomain(void) { return @TIO_STORAGE_ADDON_LITERAL; }
static inline NSString *TIOStorageProfileDomain(void) { return @TIO_STORAGE_PROFILE_LITERAL; }
static inline NSString *TIOStorageKnowledgeDomain(void) { return @TIO_STORAGE_KNOWLEDGE_LITERAL; }
static inline NSString *TIOStoragePreviewDomain(void) { return @TIO_STORAGE_PREVIEW_LITERAL; }
static inline NSString *TIOStorageNavigationDomain(void) { return @TIO_STORAGE_NAVIGATION_LITERAL; }
#endif
