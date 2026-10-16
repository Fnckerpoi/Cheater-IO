#include "NewsHubIdentityPolicy.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    assert(TIONewsHubBundleMatches(TIO_NEWS_TARGET_BUNDLE_ID));
    assert(TIONewsHubAddonMatches("TurboIOPrivateAddon.dylib",TIO_NEWS_EXPECTED_ADDON_UUID));
    assert(!TIONewsHubBundleMatches(NULL));assert(!TIONewsHubBundleMatches("com.rayneo.venus.pub"));
    assert(!TIONewsHubBundleMatches("com.duriea.turboio.manuscriptlab105.*"));
    assert(!TIONewsHubAddonMatches("OtherAddon.dylib",TIO_NEWS_EXPECTED_ADDON_UUID));
    assert(!TIONewsHubAddonMatches("TurboIOPrivateAddon.dylib","FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"));
    assert(!TIONewsHubAddonMatches(NULL,TIO_NEWS_EXPECTED_ADDON_UUID));assert(!TIONewsHubAddonMatches("TurboIOPrivateAddon.dylib",NULL));
    if(!strcmp(TIO_NEWS_TARGET_BUNDLE_ID,"com.duriea.turboio.research")){
        assert(!TIONewsHubBundleMatches("com.duriea.turboio.manuscriptlab105.public-20261015"));
        assert(!TIONewsHubAddonMatches("TurboIOPrivateAddon.dylib","11111111-2222-4333-8444-555555555555"));
    }else{
        assert(!TIONewsHubBundleMatches("com.duriea.turboio.research"));
        assert(!TIONewsHubBundleMatches("com.duriea.turboio.manuscriptlab105.other-public"));
        assert(!TIONewsHubAddonMatches("TurboIOPrivateAddon.dylib","3F635A04-D0C3-3196-919E-D4B73D7B0945"));
    }
    puts("EXACT_NEWS_IDENTITY_POLICY_PASS");
}
