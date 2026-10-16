#include <stdatomic.h>
static _Atomic unsigned Sequence;
unsigned NextOrder(void){return atomic_fetch_add(&Sequence,1)+1;}
