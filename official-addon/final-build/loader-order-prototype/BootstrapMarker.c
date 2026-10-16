extern unsigned NextOrder(void);
static unsigned Order;
__attribute__((constructor)) static void BootstrapInit(void){Order=NextOrder();}
unsigned BootstrapReady(void){return Order;}
