#include <dlfcn.h>
extern unsigned NextOrder(void);
static unsigned Order,LookupOrder,ObservedBootstrap;
__attribute__((constructor)) static void FlutterInit(void){
 Order=NextOrder();
 unsigned (*ready)(void)=(unsigned (*)(void))dlsym(RTLD_DEFAULT,"BootstrapReady");
 ObservedBootstrap=ready?ready():0;
 LookupOrder=NextOrder();
}
unsigned FlutterOrder(void){return Order;}
unsigned FirstLookupOrder(void){return LookupOrder;}
unsigned BootstrapAtFirstLookup(void){return ObservedBootstrap;}
