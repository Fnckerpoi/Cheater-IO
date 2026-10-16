#include <dlfcn.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
typedef void *(*Resolver)(void *,const char *);
typedef void *(*Caller)(Resolver,void *,const char *);
int main(int argc,char **argv) {
    assert(argc==4);
    void *a=dlopen(argv[1],RTLD_NOW|RTLD_GLOBAL);assert(a);
    void *n=dlopen(argv[2],RTLD_NOW|RTLD_GLOBAL);assert(n);
    void *b=dlopen(argv[3],RTLD_NOW|RTLD_GLOBAL);assert(b);
    Caller caller=(Caller)dlsym(a,"CallerLookup");Resolver naive=(Resolver)dlsym(n,"NaiveLookup");assert(caller&&naive);
    dlerror(); void *baseline=caller(dlsym,RTLD_NEXT,"bridge_collision"); const char *error=dlerror();
    printf("baseline=%d error=%s\n",baseline?((int(*)(void))baseline)():-1,error?"present":"none");
    dlerror();void *nested=caller(naive,RTLD_NEXT,"bridge_collision");error=dlerror();
    printf("nested=%d error=%s\n",nested?((int(*)(void))nested)():-1,error?"present":"none");fflush(stdout);
    assert(baseline==nested && "Ordinary C wrapper must not alter caller-sensitive RTLD_NEXT result");
    return 0;
}
