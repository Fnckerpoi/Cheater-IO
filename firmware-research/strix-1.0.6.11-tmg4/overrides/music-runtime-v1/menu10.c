#include "../camera-v1/camera_native.h"
#include "../dots-v1/dots_native.h"
#include "../context-english-v1/english_native.h"
#include "../app-runtime-v1/app_service.h"
#include "../didi-v1/ride_service.h"
/* Fourteen-item launcher retaining eight stock and six custom targets. */
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
static DRSlot *ride(void *app){return slot(app)?slot(app)->ride:NULL;}
static DotsNativeSlot *dots_menu_slot(void *app){return app&&slot(app)?slot(app)->dots:NULL;}
CENativeSlot *ce_menu_slot(void *app){return app&&slot(app)?slot(app)->english:NULL;}
static TCSlot *camera_slot(void *app){return slot(app)?slot(app)->camera:NULL;}
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
int m8_impl_frame_start(int index){return 15+30*clamp(index,0,13);}
int m8_impl_frame_index(int frame){return clamp(frame,0,419)/30;}
void m8_hook_render_slide(void *app){
 float value=*(float *)((uint8_t *)app+TIO_MENU_SCROLL_POSITION_OFFSET);if(!(value>=-1.0f))value=-1.0f;if(value>14.0f)value=14.0f;
 int frame=(int)(value*30.0f+15.5f);(void)m8_hook_frame(app,clamp(frame,0,419));
}
const char *m8_hook_app_id(int index){
  return index==13?"TurboCamera":index==12?"TurboDots":index==11?"TurboEnglish":index==10?"TurboApps":index==9?"TurboRide":index==8?"TurboDisplay":stock_app_id(index);
}
void m8_hook_names(void *app){
  {TCSlot *e=camera_slot(app);if(e&&!e->row&&ptr(app,4)){e->row=tio_lv_obj_create_ex(ptr(app,4),0);if(e->row){clean_style(e->row);tio_lv_obj_set_size(e->row,260,26);native_align(e->row,5,0,-60);hidden(e->row,true);e->label=native_label_create(e->row);if(e->label){clean_style(e->label);native_label_text(e->label,"遥控拍照");void *f=menu_font(20,0);if(f)menu_text_font(e->label,f,0);native_text_color(e->label,0x00ff00,0);menu_text_align(e->label,2,0);tio_lv_obj_set_size(e->label,260,26);native_align(e->label,9,0,0);}}}}
  {DotsNativeSlot *e=dots_menu_slot(app);if(e&&!e->row&&ptr(app,4)){e->row=tio_lv_obj_create_ex(ptr(app,4),0);if(e->row){clean_style(e->row);tio_lv_obj_set_size(e->row,260,26);native_align(e->row,5,0,-60);hidden(e->row,true);e->label=native_label_create(e->row);if(e->label){clean_style(e->label);native_label_text(e->label,"Dots");void *f=menu_font(20,0);if(f)menu_text_font(e->label,f,0);native_text_color(e->label,0x00ff00,0);menu_text_align(e->label,2,0);tio_lv_obj_set_size(e->label,260,26);native_align(e->label,9,0,0);}}}}
  CENativeSlot *e=ce_menu_slot(app);if(e&&!e->row&&ptr(app,4)){e->row=tio_lv_obj_create_ex(ptr(app,4),0);if(e->row){clean_style(e->row);tio_lv_obj_set_size(e->row,260,26);native_align(e->row,5,0,-60);hidden(e->row,true);e->label=native_label_create(e->row);if(e->label){clean_style(e->label);native_label_text(e->label,"情境英语");void *f=menu_font(20,0);if(f)menu_text_font(e->label,f,0);native_text_color(e->label,0x00ff00,0);menu_text_align(e->label,2,0);tio_lv_obj_set_size(e->label,260,26);native_align(e->label,9,0,0);}}}
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
  DRSlot *d=ride(app);if(!d)return;d->row=tio_lv_obj_create_ex(ptr(app,4),0);if(!d->row)return;clean_style(d->row);tio_lv_obj_set_size(d->row,260,26);native_align(d->row,5,0,-60);hidden(d->row,true);d->label=native_label_create(d->row);if(d->label){clean_style(d->label);native_label_text(d->label,"滴滴出行");if(font)menu_text_font(d->label,font,0);native_text_color(d->label,0x00ff00,0);menu_text_align(d->label,2,0);tio_lv_obj_set_size(d->label,260,26);native_align(d->label,9,0,0);}
}
void m8_hook_refresh_label(void *app,int index){
  if(index==13){if(camera_slot(app)&&camera_slot(app)->label)native_label_text(camera_slot(app)->label,"遥控拍照");return;}
  if(index==12){if(dots_menu_slot(app)&&dots_menu_slot(app)->label)native_label_text(dots_menu_slot(app)->label,"Dots");return;}
  if(index==11){if(ce_menu_slot(app)&&ce_menu_slot(app)->label)native_label_text(ce_menu_slot(app)->label,"情境英语");return;}
  if(index==10){if(apps(app)&&apps(app)->label)native_label_text(apps(app)->label,"我的应用");return;}
  if(index==9){if(ride(app)&&ride(app)->label)native_label_text(ride(app)->label,"滴滴出行");return;}
  if(index==8){if(tail(app)->label)native_label_text(tail(app)->label,"Turbo Display");return;}
  if(index>=0 && index<8)stock_refresh_label(app,index);
}
void m8_hook_delete_dots(void *app){
  /* Stock deletes the common parent, including our eighth child. */
  if(camera_slot(app))camera_slot(app)->dot=NULL;
  tail(app)->dot=NULL;if(ride(app))ride(app)->dot=NULL;if(apps(app))apps(app)->dot=NULL;if(ce_menu_slot(app))ce_menu_slot(app)->dot=NULL;if(dots_menu_slot(app))dots_menu_slot(app)->dot=NULL;stock_delete_dots(app);
}
void m8_hook_dots(void *app){
  stock_dots(app);M8NativeTail *t=tail(app);void *parent=ptr(app,0x6c);
  if(!parent || t->dot)return;
  t->dot=block(parent,4,4,0,0);
  if(!t->dot){t->errors|=2;return;}
  /* Native flex parent owns positioning; clear explicit alignment offset. */
  native_align(t->dot,0,0,0);tio_lv_obj_set_style_bg_opa(t->dot,127,0);
  DRSlot *d=ride(app);if(d&&!d->dot){d->dot=block(parent,4,4,0,0);if(d->dot){native_align(d->dot,0,0,0);tio_lv_obj_set_style_bg_opa(d->dot,127,0);}}
  TAPMenu *a=apps(app);if(a&&!a->dot){a->dot=block(parent,4,4,0,0);if(a->dot){native_align(a->dot,0,0,0);tio_lv_obj_set_style_bg_opa(a->dot,127,0);}}
  CENativeSlot *e=ce_menu_slot(app);if(e&&!e->dot){e->dot=block(parent,4,4,0,0);if(e->dot){native_align(e->dot,0,0,0);tio_lv_obj_set_style_bg_opa(e->dot,127,0);}}
  {DotsNativeSlot *e=dots_menu_slot(app);if(e&&!e->dot){e->dot=block(parent,4,4,0,0);if(e->dot){native_align(e->dot,0,0,0);tio_lv_obj_set_style_bg_opa(e->dot,127,0);}}}
  TCSlot *camera=camera_slot(app);if(camera&&!camera->dot){camera->dot=block(parent,4,4,0,0);if(camera->dot){native_align(camera->dot,0,0,0);tio_lv_obj_set_style_bg_opa(camera->dot,127,0);}}
}
void m8_hook_lottie(void *app){
  {TCSlot *e=camera_slot(app);if(e&&!e->icon&&ptr(app,4)){e->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(e->icon){clean_style(e->icon);tio_lv_obj_set_size(e->icon,65,65);native_align(e->icon,2,0,52);hidden(e->icon,true);const int lines[][4]={{48,3,8,18},{48,3,8,50},{3,35,8,18},{3,35,53,18},{25,3,20,10},{3,10,20,10},{3,10,43,10},{20,3,23,27},{20,3,23,42},{3,18,23,27},{3,18,40,27}};for(unsigned i=0;i<sizeof lines/sizeof *lines;i++)if(!block(e->icon,lines[i][0],lines[i][1],lines[i][2],lines[i][3])){tio_lv_obj_delete(e->icon);e->icon=NULL;break;}}}}
  {DotsNativeSlot *e=dots_menu_slot(app);if(e&&!e->icon&&ptr(app,4)){e->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(e->icon){clean_style(e->icon);tio_lv_obj_set_size(e->icon,65,65);native_align(e->icon,2,0,52);hidden(e->icon,true);const int lines[][4]={{48,3,8,10},{3,36,8,10},{3,36,53,10},{36,3,17,43},{3,10,17,43},{4,4,19,25},{4,4,30,25},{4,4,41,25}};for(unsigned i=0;i<sizeof lines/sizeof *lines;i++)if(!block(e->icon,lines[i][0],lines[i][1],lines[i][2],lines[i][3])){tio_lv_obj_delete(e->icon);e->icon=NULL;break;}}}}
  CENativeSlot *e=ce_menu_slot(app);if(e&&!e->icon&&ptr(app,4)){e->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(e->icon){clean_style(e->icon);tio_lv_obj_set_size(e->icon,65,65);native_align(e->icon,2,0,52);hidden(e->icon,true);const int lines[][4]={{3,42,8,10},{3,42,32,10},{3,42,55,10},{47,3,8,10},{47,3,8,50},{15,3,13,22},{15,3,37,22},{15,3,13,32},{15,3,37,32}};for(unsigned i=0;i<sizeof lines/sizeof *lines;i++)if(!block(e->icon,lines[i][0],lines[i][1],lines[i][2],lines[i][3])){tio_lv_obj_delete(e->icon);e->icon=NULL;break;}}}
  TAPMenu *a=apps(app);if(a&&!a->icon&&ptr(app,4)){a->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(a->icon){clean_style(a->icon);tio_lv_obj_set_size(a->icon,65,65);native_align(a->icon,2,0,52);hidden(a->icon,true);for(unsigned i=0;i<4;i++)if(!block(a->icon,18,18,10+25*(i%2),10+25*(i/2))){tio_lv_obj_delete(a->icon);a->icon=NULL;break;}}}
  DRSlot *d=ride(app);if(d&&!d->icon&&ptr(app,4)){d->icon=tio_lv_obj_create_ex(ptr(app,4),0);if(d->icon){clean_style(d->icon);tio_lv_obj_set_size(d->icon,65,65);native_align(d->icon,2,0,52);hidden(d->icon,true);const int car[][4]={{40,3,12,18},{3,22,8,28},{3,22,55,28},{47,3,9,48},{9,4,15,38},{9,4,41,38},{24,3,20,10},{3,10,18,15},{3,10,44,15}};for(unsigned i=0;i<sizeof car/sizeof *car;i++)if(!block(d->icon,car[i][0],car[i][1],car[i][2],car[i][3])){tio_lv_obj_delete(d->icon);d->icon=NULL;break;}}}
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
  frame=clamp(frame,0,419);
  for(unsigned i=0;i<14;i++){
    void *row=i==13?(camera_slot(app)?camera_slot(app)->row:NULL):i==12?(dots_menu_slot(app)?dots_menu_slot(app)->row:NULL):i==11?(ce_menu_slot(app)?ce_menu_slot(app)->row:NULL):i==10?(apps(app)?apps(app)->row:NULL):i==9?(ride(app)?ride(app)->row:NULL):i==8?tail(app)->row:ptr(app,0xc+4*i);
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
  frame=clamp(frame,15,405);
  for(unsigned i=0;i<14;i++){
    void *dot=i==13?(camera_slot(app)?camera_slot(app)->dot:NULL):i==12?(dots_menu_slot(app)?dots_menu_slot(app)->dot:NULL):i==11?(ce_menu_slot(app)?ce_menu_slot(app)->dot:NULL):i==10?(apps(app)?apps(app)->dot:NULL):i==9?(ride(app)?ride(app)->dot:NULL):i==8?tail(app)->dot:ptr(app,0x70+4*i);if(!dot)continue;
    int d=frame-m8_hook_frame_start((int)i);if(d<0)d=-d;
    int weight=30-clamp(d,0,30);
    tio_lv_obj_set_size(dot,4+(8*weight+15)/30,4);
    tio_lv_obj_set_style_bg_opa(dot,(uint8_t)(127+(128*weight+15)/30),0);
  }
}
bool m8_hook_frame(void *app,int frame){
  void *lottie=ptr(app,8);if(!lottie || frame<0 || frame>419)return false;
  int previous=(int)*word(app,0xa0);
  if(previous==frame)return false;
  *word(app,0xa0)=(uint32_t)frame;
  /* Native resource contains eight icons. Never feed it a ninth frame
   * segment: cross-fade its final stable frame with our LVGL glyph. */
  int mix=clamp(frame-225,0,30);
  menu_lottie_frame(lottie,frame<=225?frame:225);
  menu_opa(lottie,(uint8_t)(255*(30-mix)/30),0);
  void *icon=tail(app)->icon;
  int displayweight=mix-clamp(frame-255,0,30);
  if(icon){hidden(icon,displayweight==0);menu_opa(icon,(uint8_t)(255*displayweight/30),0);menu_translate(icon,55*(30-mix)/30,0);}
  int ridemix=clamp(frame-255,0,30),appmix=clamp(frame-285,0,30),englishmix=clamp(frame-315,0,30),dotsmix=clamp(frame-345,0,30),cameramix=clamp(frame-375,0,30);
  void *icons[]={ride(app)?ride(app)->icon:NULL,apps(app)?apps(app)->icon:NULL,ce_menu_slot(app)?ce_menu_slot(app)->icon:NULL,dots_menu_slot(app)?dots_menu_slot(app)->icon:NULL,camera_slot(app)?camera_slot(app)->icon:NULL};
  int weights[]={ridemix-appmix,appmix-englishmix,englishmix-dotsmix,dotsmix-cameramix,cameramix};int starts[]={ridemix,appmix,englishmix,dotsmix,cameramix};
  for(unsigned i=0;i<5;i++)if(icons[i]){hidden(icons[i],weights[i]==0);menu_opa(icons[i],(uint8_t)(255*weights[i]/30),0);menu_translate(icons[i],55*(30-starts[i])/30,0);}
  m8_hook_indicator(app,frame,previous);fm_sync(app);return true;
}
void *m8_hook_ctor(void *app){
  stock_ctor(app);M8NativeTail *t=tail(app);
  for(unsigned i=0;i<sizeof *t;i++)((volatile uint8_t *)t)[i]=0;
  t->navigation=tn_slot_create(app);
  if(slot(app)){slot(app)->ride=dr_slot_create(app);slot(app)->apps=tap_menu_create(app);slot(app)->english=ce_slot_create(app);slot(app)->dots=dots_slot_create(app);slot(app)->camera=tc_slot_create(app);}
  return app;
}
void m8_hook_show(void *app){retire(app);stock_show(app);m8_hook_label_index(app,(int)*word(app,0x98));fm_show(app);}
void m8_hook_hide(void *app){tc_slot_hidden(camera_slot(app));dots_slot_hidden(dots_menu_slot(app));ce_slot_hidden(ce_menu_slot(app));tap_service_hidden(app);fm_hide(app);native_unregister_wheel(app);retire(app);dr_slot_hidden(ride(app));stock_hide(app);}
void m8_hook_destroy(void *app){tc_slot_destroy(camera_slot(app));if(slot(app))slot(app)->camera=NULL;dots_slot_destroy(dots_menu_slot(app));if(slot(app))slot(app)->dots=NULL;ce_slot_destroy(ce_menu_slot(app));if(slot(app))slot(app)->english=NULL;fm_destroy(app);
  native_unregister_wheel(app);retire(app);
  tap_menu_destroy(apps(app));if(slot(app))slot(app)->apps=NULL;
  dr_slot_destroy(ride(app));if(slot(app))slot(app)->ride=NULL;
  td_slot_destroy(slot(app));
  tn_slot_destroy(slot(app));tail(app)->navigation=NULL;
  /* Only the stock parent destructor owns row/icon/dot. Do not double-delete. */
  tail(app)->row=tail(app)->label=tail(app)->icon=tail(app)->dot=NULL;
  stock_destroy(app);
}
void m8_hook_wheel(void *app,int delta){
  if(tc_native_visible(app)){tc_native_wheel(app,delta);return;}
  if(dots_native_visible(app)){dots_native_wheel(app,delta);return;}
  if(ce_native_visible(app))return;
  if(tap_service_visible(app)){tap_service_wheel(app,delta);return;}
  if(dr_slot_visible(ride(app))){dr_slot_wheel(ride(app),delta);return;}
  if(tail(app)->page)return;
  stock_wheel(app,delta);
}
bool m8_hook_event(void *app,void *event){
  if(native_event_code(event)!=0xe || native_event_key(event)!=0x3a)return stock_event(app,event);
  if(tc_native_visible(app))return true;
  if(dots_native_visible(app)||ce_native_visible(app)||tap_service_visible(app)||tail(app)->page)return true;
  if(dr_slot_visible(ride(app)))return true;
  /* Preserve native eight-item eligibility and keyboard behavior exactly. */
  if(*word(app,0x98)<8)return stock_event(app,event);
  if(!eligible(app))return false;
  native_snap(app); /* Same stock snap BEFORE deciding which item to launch. */
  unsigned index=*word(app,0x98);
  if(index<8)return stock_event(app,event);
  if(index==13){trace(120,tc_slot_open(camera_slot(app)));return true;}
  if(index==12){trace(110,dots_slot_open(dots_menu_slot(app)));return true;}
  if(index==11){trace(100,ce_slot_open(ce_menu_slot(app)));return true;}
  if(index==10){trace(90,tap_service_open(app));return true;}
  if(index==9){trace(80,dr_slot_open(ride(app)));return true;}
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
  if(dots_native_file(file,stream_queue_send)!=TIO_FOREIGN)return;
  if(ce_native_file_receive(file,stream_queue_send)!=TIO_FOREIGN)return;
  TIOImageResult apps=tap_service_file(file,stream_queue_send);if(apps!=TIO_FOREIGN)return;
  TIOImageResult ride=dr_file_receive(file,stream_queue_send);if(ride!=TIO_FOREIGN)return;
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
  tc_native_message(message);
  tgr_native_message(message); /* borrowed bytes; stock handler still disposes */
  if(dots_message_is_ours(message)){dots_native_message(message);return;}
  if(ce_message_is_ours(message)){ce_native_message_dispatch(message);return;}
  if(tap_service_ours(message)){tap_service_message(message);return;}
  if(dr_message_is_ours(message)){dr_message_dispatch(message);return;}
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