#include "../app-runtime-v1/app_service.h"
/* Ten-item launcher retaining eight stock and two custom targets. */
#include "../navigation-runtime-v1/menu9.h"
#include "../menu8-renderer.h"
#include "../focus-v1/focus_menu.h"
#include "../diagnostics-v1/diagnostics_service.h"
#if TIO_DISPLAY_RUNTIME
#include "../display-runtime-v1/native_display_file.h"
#define m8_page_open tdp_native_open
#define m8_page_retire tdp_native_retire
#else
#define m8_page_open tio_native_page_open
#define m8_page_retire tio_native_page_retire
#endif
extern const M8RenderAPI m8_native_api;
extern void *stock_ctor(void *);
extern void stock_show(void *),stock_hide(void *),stock_destroy(void *),stock_wheel(void *,int);
extern bool stock_event(void *,void *);
extern void stock_names(void *),stock_dots(void *),stock_delete_dots(void *),stock_lottie(void *);
extern const char *stock_app_id(int);
extern void stock_refresh_label(void *,int);
extern void stock_message(void *,const TIONativeMessage *);
extern void native_unregister_wheel(void *),native_snap(void *),native_report_activity(void);
extern bool native_wake_overlay(void),native_has_flag(void *,uint32_t);
extern unsigned native_event_code(void *),native_event_key(void *);
extern void *native_label_create(void *);
extern void native_label_text(void *,const char *),native_text_color(void *,uint32_t,uint32_t);
extern void native_align(void *,int,int,int),native_log(unsigned,unsigned,unsigned,unsigned,const char *,...);
extern void *tio_lv_obj_create_ex(void *,uint32_t);
extern void tio_lv_obj_delete(void *),tio_lv_obj_set_size(void *,int,int);
extern void tio_lv_obj_add_flag(void *,uint32_t),tio_lv_obj_remove_flag(void *,uint32_t);
extern void tio_lv_obj_set_style_bg_color(void *,uint32_t,uint32_t);
extern void tio_lv_obj_set_style_bg_opa(void *,uint8_t,uint32_t);
extern void tio_lv_obj_set_style_border_width(void *,int,uint32_t);
extern void tio_lv_obj_set_style_radius(void *,int,uint32_t);
extern void menu_remove_styles(void *),menu_opa(void *,uint8_t,uint32_t);
extern void menu_translate(void *,int,uint32_t),menu_lottie_frame(void *,int);
extern void *menu_font(int,int);
extern void menu_text_font(void *,void *,uint32_t),menu_text_align(void *,int,uint32_t);
extern uint32_t stream_tick(void);
extern int stream_queue_send(unsigned,const TIONativeMessage *);

static M8NativeTail *tail(void *app){return (M8NativeTail *)((uint8_t *)app+TIO_STOCK_MENU_BYTES);}
static TNNavSlot *slot(void *app){return tail(app)->navigation;}
static TAPMenu *apps(void *app){return slot(app)?slot(app)->apps:NULL;}
static void *ptr(void *app,unsigned off){return *(void **)((uint8_t *)app+off);}
static uint32_t *word(void *app,unsigned off){return (uint32_t *)((uint8_t *)app+off);}
static bool byte(void *app,unsigned off){return *((uint8_t *)app+off)!=0;}
static int clamp(int v,int lo,int hi){return v<lo?lo:v>hi?hi:v;}
static void trace(unsigned e,unsigned r){native_log(1,0,0,0,"[TurboMenu8] event=%u result=%u",e,r);}
static bool eligible(void *app){
  void *root=ptr(app,4);
  return root && ptr(app,8) && byte(app,0xa4) && !byte(app,TIO_MENU_EXTERNAL_ACTION_OFFSET) &&
    !native_has_flag(root,1) && !native_wake_overlay();
}
static void hidden(void *obj,bool hide){if(obj)m8_native_api.hidden(obj,hide);}
static void retire(void *app){
  M8NativeTail *t=tail(app);
  if(t->page)m8_page_retire(t->page);
  t->page=NULL;
}
static void clean_style(void *obj){
  menu_remove_styles(obj);
  tio_lv_obj_remove_flag(obj,2);tio_lv_obj_remove_flag(obj,16);
}
static void *block(void *parent,int w,int h,int x,int y){
  void *o=tio_lv_obj_create_ex(parent,0);if(!o)return NULL;
  clean_style(o);tio_lv_obj_set_size(o,w,h);
  tio_lv_obj_set_style_bg_color(o,0x00ff00,0);
  tio_lv_obj_set_style_bg_opa(o,255,0);native_align(o,1,x,y);return o;
}
/* Assembler entry shims preserve r1-r3: stock callers use whole-unit register
 * knowledge beyond ordinary AAPCS (snap keeps a frame in r2 across this call). */
int m8_impl_frame_start(int index){return 15+30*clamp(index,0,9);}
int m8_impl_frame_index(int frame){return clamp(frame,0,299)/30;}
void m8_hook_render_slide(void *app){
 float value=*(float *)((uint8_t *)app+TIO_MENU_SCROLL_POSITION_OFFSET);if(!(value>=-1.0f))value=-1.0f;if(value>10.0f)value=10.0f;
 int frame=(int)(value*30.0f+15.5f);(void)m8_hook_frame(app,clamp(frame,0,299));
}
const char *m8_hook_app_id(int index){
  return index==9?"TurboApps":index==8?"TurboDisplay":stock_app_id(index);
}
void m8_hook_names(void *app){
  TAPMenu *a=apps(app);if(a&&!a->row&&ptr(app,4)){a->row=tio_lv_obj_create_ex(ptr(app,4),0);if(a->row){clean_style(a->row);tio_lv_obj_set_size(a->row,260,26);native_align(a->row,5,0,-60);hidden(a->row,true);a->label=native_label_create(a->row);if(a->label){clean_style(a->label);native_label_text(a->label,"我的应用");void *f=menu_font(20,0);if(f)menu_text_font(a->label,f,0);native_text_color(a->label,0x00ff00,0);menu_text_align(a->label,2,0);tio_lv_obj_set_size(a->label,260,26);native_align(a->label,9,0,0);}}}
  stock_names(app);M8NativeTail *t=tail(app);
  if(t->row || !ptr(app,4))return;
  void *row=tio_lv_obj_create_ex(ptr(app,4),0);if(!row){t->errors|=1;return;}
  clean_style(row);tio_lv_obj_set_size(row,260,26);native_align(row,5,0,-60);
  hidden(row,true);
  void *label=native_label_create(row);
  if(!label){tio_lv_obj_delete(row);t->errors|=1;return;}
  clean_style(label);native_label_text(label,"Turbo Display");
  void *font=menu_font(20,0);if(font)menu_text_font(label,font,0);
  native_text_color(label,0x00ff00,0);menu_text_align(label,2,0);
  tio_lv_obj_set_size(label,260,26);native_align(label,9,0,0);
  t->row=row;t->label=label;trace(30,8);
}
void m8_hook_refresh_label(void *app,int index){
  if(index==9){if(apps(app)&&apps(app)->label)native_label_text(apps(app)->label,"我的应用");return;}
  if(index==8){if(tail(app)->label)native_label_text(tail(app)->label,"Turbo Display");return;}
  if(index>=0 && index<8)stock_refresh_label(app,index);
}
void m8_hook_delete_dots(void *app){
  /* Stock deletes the common parent, including our eighth child. */
  tail(app)->dot=NULL;if(apps(app))apps(app)->dot=NULL;stock_delete_dots(app);
}
void m8_hook_dots(void *app){
  stock_dots(app);M8NativeTail *t=tail(app);void *parent=ptr(app,0x6c);
  if(!parent || t->dot)return;
  t->dot=block(parent,4,4,0,0);
  if(!t->dot){t->errors|=2;return;}
  /* Native flex parent owns positioning; clear explicit alignment offset. */
  native_align(t->dot,0,0,0);tio_lv_obj_set_style_bg_opa(t->dot,127,0);
  TAPMenu *a=apps(app);if(a&&!a->dot){a->dot=block(parent,4,4,0,0);if(a->dot){native_align(a->dot,0,0,0);tio_lv_obj_set_style_bg_opa(a->dot,127,0);}}
}
void m8_hook_lottie(void *app){
  TAPMenu *a=apps(app);if(a&&!a->icon&&ptr(app,4)){a->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(a->icon){clean_style(a->icon);tio_lv_obj_set_size(a->icon,65,65);native_align(a->icon,2,0,52);hidden(a->icon,true);for(unsigned i=0;i<4;i++)if(!block(a->icon,18,18,10+25*(i%2),10+25*(i/2))){tio_lv_obj_delete(a->icon);a->icon=NULL;break;}}}
  stock_lottie(app);M8NativeTail *t=tail(app);
  if(t->icon || !ptr(app,4))return;
  void *icon=tio_lv_obj_create_ex(ptr(app,4),0);if(!icon){t->errors|=4;return;}
  clean_style(icon);tio_lv_obj_set_size(icon,65,65);native_align(icon,2,0,52);
  hidden(icon,true);
  /* Small native LVGL picture-frame glyph; no PNG decode buffer or asset-pack
   * mutation. All children are owned and destroyed with icon. */
  const int rects[][4]={{55,3,5,8},{55,3,5,54},{3,49,5,8},{3,49,57,8},
    {8,8,42,17},{5,6,13,43},{5,12,18,37},{5,19,23,30},{5,12,28,37},
    {5,7,33,42},{5,13,38,36},{5,7,43,42},{6,4,48,45}};
  for(unsigned i=0;i<sizeof rects/sizeof *rects;i++){
    if(!block(icon,rects[i][0],rects[i][1],rects[i][2],rects[i][3])){
      tio_lv_obj_delete(icon);t->errors|=4;return;
    }
  }
  t->icon=icon;
}
void m8_hook_label_frame(void *app,int frame){
  frame=clamp(frame,0,299);
  for(unsigned i=0;i<10;i++){
    void *row=i==9?(apps(app)?apps(app)->row:NULL):i==8?tail(app)->row:ptr(app,0xc+4*i);
    if(!row)continue;
    int d=frame-m8_hook_frame_start((int)i),distance=d<0?-d:d;
    if(distance>=30){hidden(row,true);continue;}
    hidden(row,false);menu_opa(row,(uint8_t)(255*(30-distance)/30),0);
    menu_translate(row,-55*d/30,0);
  }
  *word(app,0x9c)=(uint32_t)m8_hook_frame_index(frame);fm_sync(app);
}
void m8_hook_label_index(void *app,int index){m8_hook_label_frame(app,m8_hook_frame_start(index));}
void m8_hook_indicator(void *app,int frame,int previous){
  (void)previous;if(!ptr(app,0x6c))return;
  frame=clamp(frame,15,285);
  for(unsigned i=0;i<10;i++){
    void *dot=i==9?(apps(app)?apps(app)->dot:NULL):i==8?tail(app)->dot:ptr(app,0x70+4*i);if(!dot)continue;
    int d=frame-m8_hook_frame_start((int)i);if(d<0)d=-d;
    int weight=30-clamp(d,0,30);
    tio_lv_obj_set_size(dot,4+(8*weight+15)/30,4);
    tio_lv_obj_set_style_bg_opa(dot,(uint8_t)(127+(128*weight+15)/30),0);
  }
}
bool m8_hook_frame(void *app,int frame){
  void *lottie=ptr(app,8);if(!lottie || frame<0 || frame>299)return false;
  int previous=(int)*word(app,0xa0);
  if(previous==frame)return false;
  *word(app,0xa0)=(uint32_t)frame;
  /* Native resource contains eight icons. Never feed it a ninth frame
   * segment: cross-fade its final stable frame with our LVGL glyph. */
  int mix=clamp(frame-225,0,30);
  menu_lottie_frame(lottie,frame<=225?frame:225);
  menu_opa(lottie,(uint8_t)(255*(30-mix)/30),0);
  void *icon=tail(app)->icon;
  int appmix=clamp(frame-255,0,30),displayweight=mix-appmix;
  if(icon){hidden(icon,displayweight==0);menu_opa(icon,(uint8_t)(255*displayweight/30),0);menu_translate(icon,55*(30-mix)/30,0);}
  void *apps_icon=apps(app)?apps(app)->icon:NULL;
  if(apps_icon){hidden(apps_icon,appmix==0);menu_opa(apps_icon,(uint8_t)(255*appmix/30),0);menu_translate(apps_icon,55*(30-appmix)/30,0);}
  m8_hook_indicator(app,frame,previous);fm_sync(app);return true;
}
void *m8_hook_ctor(void *app){
  stock_ctor(app);M8NativeTail *t=tail(app);
  for(unsigned i=0;i<sizeof *t;i++)((volatile uint8_t *)t)[i]=0;
  t->navigation=tn_slot_create(app);
  if(slot(app))slot(app)->apps=tap_menu_create(app);
  return app;
}
void m8_hook_show(void *app){retire(app);stock_show(app);m8_hook_label_index(app,(int)*word(app,0x98));fm_show(app);}
void m8_hook_hide(void *app){tap_service_hidden(app);fm_hide(app);native_unregister_wheel(app);retire(app);stock_hide(app);}
void m8_hook_destroy(void *app){fm_destroy(app);
  native_unregister_wheel(app);retire(app);
  tap_menu_destroy(apps(app));if(slot(app))slot(app)->apps=NULL;
  td_slot_destroy(slot(app));
  tn_slot_destroy(slot(app));tail(app)->navigation=NULL;
  /* Only the stock parent destructor owns row/icon/dot. Do not double-delete. */
  tail(app)->row=tail(app)->label=tail(app)->icon=tail(app)->dot=NULL;
  stock_destroy(app);
}
void m8_hook_wheel(void *app,int delta){
  if(tap_service_visible(app)){tap_service_wheel(app,delta);return;}
  if(tail(app)->page)return;
  stock_wheel(app,delta);
}
bool m8_hook_event(void *app,void *event){
  if(native_event_code(event)!=0xe || native_event_key(event)!=0x3a)return stock_event(app,event);
  if(tap_service_visible(app)||tail(app)->page)return true;
  /* Preserve native eight-item eligibility and keyboard behavior exactly. */
  if(*word(app,0x98)<8)return stock_event(app,event);
  if(!eligible(app))return false;
  native_snap(app); /* Same stock snap BEFORE deciding which item to launch. */
  unsigned index=*word(app,0x98);
  if(index<8)return stock_event(app,event);
  if(index==9){trace(90,tap_service_open(app));return true;}
  if(index!=8){trace(39,index);return true;}
  M8NativeTail *t=tail(app);uint32_t sid=stream_tick();
  if(sid<=t->last_session)sid=t->last_session+1u;
  if(!sid){trace(38,0);return true;}
  t->last_session=sid;
  bool ok=m8_page_open(ptr(app,4),sid,&t->page)!=NULL;
  native_report_activity();trace(31,ok);
  return true; /* Failure consumes the custom action without launching a stock item. */
}
void tio_hook_file(const TIONativeFile *file){
  TIOImageResult apps=tap_service_file(file,stream_queue_send);if(apps!=TIO_FOREIGN)return;
  TIOImageResult diag=td_file_receive(file,stream_queue_send);if(diag!=TIO_FOREIGN)return;
#if TIO_DISPLAY_RUNTIME
  TIOImageResult r=tdp_native_file_receive(file,stream_queue_send);
#else
  TIOImageResult r=tio_native_file_receive(file,stream_queue_send);
#endif
  if(r!=TIO_FOREIGN)trace(20,(unsigned)r);
}
extern void tgr_native_message(const TIONativeMessage *);
void m8_hook_message(void *handler,const TIONativeMessage *message){
  tgr_native_message(message); /* borrowed bytes; stock handler still disposes */
  if(tap_service_ours(message)){tap_service_message(message);return;}
  if(td_message_is_ours(message)){td_message_dispatch(message);return;}
#if TIO_DISPLAY_RUNTIME
  if(!tdp_native_message_is_ours(message)){stock_message(handler,message);return;}
#else
  if(!tio_native_message_is_ours(message)){stock_message(handler,message);return;}
#endif
  void *launcher=*(void **)(uintptr_t)0x199595d4;
  void *manager=launcher?ptr(launcher,0x15c):NULL;
  void *app=manager?ptr(manager,0x10):NULL;
#if TIO_DISPLAY_RUNTIME
  trace(21,(unsigned)tdp_native_dispatch(app?tail(app)->page:NULL,message));
#else
  trace(21,(unsigned)tio_native_page_message(app?tail(app)->page:NULL,message));
#endif
}
