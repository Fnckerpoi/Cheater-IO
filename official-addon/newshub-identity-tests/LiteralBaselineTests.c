#include <assert.h>
#include <stdbool.h>
#include <string.h>
static bool LegacyLiteralPolicy(const char *bundle,const char *uuid) {
    return !strcmp(bundle,"com.duriea.turboio.research") && !strcmp(uuid,"3F635A04-D0C3-3196-919E-D4B73D7B0945");
}
int main(void) {
    assert(LegacyLiteralPolicy("com.duriea.turboio.research","3F635A04-D0C3-3196-919E-D4B73D7B0945"));
    assert(LegacyLiteralPolicy("com.duriea.turboio.manuscriptlab105.public-20261015","11111111-2222-4333-8444-555555555555"));
}
