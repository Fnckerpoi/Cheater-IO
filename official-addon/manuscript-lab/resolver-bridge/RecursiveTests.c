#include "ResolverBridge.h"
#include <dlfcn.h>
#include <assert.h>
#include <stdio.h>
int main(int argc,char **argv) {
    assert(argc==3);
    void *ordinary=dlopen(argv[1],RTLD_NOW|RTLD_LOCAL);assert(ordinary);
    void *recursive=dlopen(argv[2],RTLD_NOW|RTLD_LOCAL);assert(recursive);
    assert(TIOResolverBridgeBind(dlsym));
    void (*configure)(TIOResolverFunction,void *)=dlsym(recursive,"BridgeFixtureConfigureRecursive");
    unsigned (*count)(void)=dlsym(recursive,"BridgeFixtureRecursiveCount");assert(configure&&count);configure(TIOResolverBridge,ordinary);
    dlerror();int (*a)(void)=dlsym(recursive,"bridge_recursive_direct");assert(a&&a()==987);assert(!dlerror());
    dlerror();int (*b)(void)=TIOResolverBridge(recursive,"bridge_recursive_tail");assert(b&&b()==987);assert(!dlerror());
    assert(a==b && count()==2);
    puts("BRIDGE_REAL_REENTRANT_DYLD_RESOLVER_PASS: nested real dlsym during symbol resolver, no fake callback pointers");
    return 0;
}
