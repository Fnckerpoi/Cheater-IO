#include "LabGate.h"
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

bool TIOManuscriptLabWritesEnabled(void) { return false; }
bool TIOManuscriptLabCollectionEnabled(void) { return false; }

bool TIOManuscriptLabIdentityAllowed(const char *bundle, const char *run, const char *version, const char *build, const char *stage) {
    if (!bundle || !run || !version || !build || !stage) return false;
    size_t n = strlen(run);
    if (n < 6 || n > 40 || !strcmp(run,"research") || !strcmp(run,"official") || run[0]=='-') return false;
    for (size_t i=0;i<n;i++) if (!((run[i]>='a'&&run[i]<='z')||(run[i]>='0'&&run[i]<='9')||run[i]=='-')) return false;
    char expected[160];
    int length=snprintf(expected,sizeof(expected),"com.duriea.turboio.manuscriptlab105.%s",run);
    return length>0 && (size_t)length<sizeof(expected) && !strcmp(bundle,expected) && !strcmp(version,"1.0.5") && !strcmp(build,"201") && !strcmp(stage,"A_DISABLED");
}

static bool canonical(const char *path, char *resolved) {
    if (!path || path[0]!='/' || strlen(path)>=PATH_MAX || strstr(path,"//")) return false;
    char current[PATH_MAX];
    size_t length=strlen(path);
    for (size_t i=1;i<=length;i++) {
        if (path[i]!='/' && path[i]!='\0') continue;
        memcpy(current,path,i);current[i]='\0';
        const char *leaf=strrchr(current,'/');leaf=leaf?leaf+1:current;
        if (!strcmp(leaf,".") || !strcmp(leaf,"..") || !*leaf) return false;
        struct stat st;
        if (lstat(current,&st) || S_ISLNK(st.st_mode)) return false;
    }
    return realpath(path,resolved)!=NULL && !strcmp(path,resolved);
}

bool TIOManuscriptLabPathAllowed(const char *home, const char *candidate) {
    char h[PATH_MAX],p[PATH_MAX],root[PATH_MAX];
    if (!canonical(home,h)||!canonical(candidate,p)) return false;
    const char *suffixes[]={"/Library/Application Support/ManuscriptLab","/tmp/ManuscriptLab"};
    for (size_t i=0;i<2;i++) {
        int count=snprintf(root,sizeof(root),"%s%s",h,suffixes[i]);
        if (count<=0 || (size_t)count>=sizeof(root)) return false;
        size_t n=strlen(root);
        if (!strncmp(root,p,n) && (p[n]=='\0'||p[n]=='/')) return true;
    }
    return false;
}
