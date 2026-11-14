#include "nav_service.h"
#include "../camera-v1/camera_native.h"
#include "../dots-v1/dots_native.h"
#include "../context-english-v1/english_native.h"
#include "../app-runtime-v1/app_service.h"
#include "../didi-v1/ride_service.h"
#include <string.h>
extern void *stream_memalign(size_t,size_t);
extern void stream_free(void *),stock_vm_event(void *);
static uint32_t crc_step(uint32_t c,uint8_t b){c^=b;for(unsigned k=0;k<8;k++)c=(c>>1)^((0u-(c&1u))&0xedb88320u);return c;}
uint32_t tn_crc(const uint8_t *p,size_t n){uint32_t c=~0u;for(size_t i=0;i<n;i++)c=crc_step(c,p[i]);return ~c;}
TNNavSlot *tn_slot_create(void *app){TNNavSlot *s=stream_memalign(8,sizeof *s);if(s){memset(s,0,sizeof *s);s->app=app;}return s;}
void tn_slot_destroy(TNNavSlot *s){if(s)stream_free(s);}
void m8_hook_vm_event(void *event){if(tc_native_event(event)||dots_native_event(event)||ce_native_handle_event(event)||tap_service_event(event)||dr_handle_event(event))return;stock_vm_event(event);}
