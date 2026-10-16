#include <dlfcn.h>
typedef void *(*Resolver)(void *,const char *);
int bridge_collision(void) { return 111; }
__attribute__((noinline)) void *CallerLookup(Resolver resolver,void *handle,const char *name) {
    void *result=resolver(handle,name);
    __asm__ volatile("" : "+r"(result));
    return result;
}
