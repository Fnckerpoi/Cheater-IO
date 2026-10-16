#include <assert.h>
#include <stdio.h>
extern unsigned FlutterOrder(void);
extern unsigned FirstLookupOrder(void);
extern unsigned BootstrapAtFirstLookup(void);
int main(void){
 unsigned bootstrap=BootstrapAtFirstLookup(),flutter=FlutterOrder(),lookup=FirstLookupOrder();
 assert(bootstrap>0 && bootstrap<flutter && flutter<lookup);
 printf("DIRECT_RUNNER_STRONG_DEPENDENCY_GREEN bootstrap=%u flutter=%u first_lookup=%u\n",bootstrap,flutter,lookup);
 return 0;
}
