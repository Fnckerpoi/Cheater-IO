#import "NewsHubNavigation.h"
#import <dispatch/dispatch.h>

void TIONewsHubInstallUI(void) {
    dispatch_async(dispatch_get_main_queue(),^{ TIONewsHubNavigationInstall(); });
}
__attribute__((constructor)) static void LoadNewsHub(void) {
    TIONewsHubInstallUI();
}
