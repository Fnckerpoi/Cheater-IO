#include <assert.h>
#include <string.h>
#include TMG4_MENU_SOURCE

int main(void) {
    static const char *expected[] = {"录音", "实时字幕", "有问必答", "待办", "导航", "提词器", "全天智记", "勿扰模式", "显示测试", "我的应用"};
    assert(FM_ITEMS == sizeof expected / sizeof expected[0]);
    for (unsigned i = 0; i < FM_ITEMS; ++i) assert(strcmp(names[i], expected[i]) == 0);
    return 0;
}
