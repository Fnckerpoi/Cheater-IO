#include "LabGate.h"
#include <assert.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
int main(void) {
    const char *bundle="com.duriea.turboio.manuscriptlab105.public-20261014";
    assert(TIOManuscriptLabIdentityAllowed(bundle,"public-20261014","1.0.5","201","A_DISABLED"));
    assert(!TIOManuscriptLabIdentityAllowed("com.rayneo.venus.pub","public-20261014","1.0.5","201","A_DISABLED"));
    assert(!TIOManuscriptLabIdentityAllowed(bundle,"public-20261014","1.0.6","218","A_DISABLED"));
    assert(!TIOManuscriptLabIdentityAllowed(bundle,"public-20261014","1.0.5","201","ENABLED"));
    assert(!TIOManuscriptLabWritesEnabled());assert(!TIOManuscriptLabCollectionEnabled());
    char temp[]="/tmp/manuscript-gate-XXXXXX",home[PATH_MAX],path[PATH_MAX],link[PATH_MAX];
    assert(mkdtemp(temp));assert(realpath(temp,home));
    assert(snprintf(path,sizeof(path),"%s/tmp",home)>0);assert(!mkdir(path,0700));
    assert(snprintf(path,sizeof(path),"%s/tmp/ManuscriptLab",home)>0);assert(!mkdir(path,0700));
    assert(TIOManuscriptLabPathAllowed(home,path));assert(!TIOManuscriptLabPathAllowed(home,home));
    assert(snprintf(link,sizeof(link),"%s/link",path)>0);assert(!symlink(home,link));
    assert(!TIOManuscriptLabPathAllowed(home,link));assert(!TIOManuscriptLabPathAllowed(home,"/tmp"));
    assert(!unlink(link));assert(!rmdir(path));assert(snprintf(path,sizeof(path),"%s/tmp",home)>0);assert(!rmdir(path));assert(!rmdir(home));
    puts("LAB_NATIVE_GATES_PASS: identity, version, disabled writes/collection, exact paths, symlink refusal");
    return 0;
}
