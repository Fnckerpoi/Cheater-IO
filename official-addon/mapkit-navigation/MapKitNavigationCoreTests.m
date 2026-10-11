#import "MapKitNavigationCore.h"
#include <math.h>
#include <stdio.h>
#define CHECK(...) do { if(!(__VA_ARGS__)){fprintf(stderr,"FAIL line %d: %s\n",__LINE__,#__VA_ARGS__);return 1;} } while(0)
int main(void){@autoreleasepool{
    CHECK([TIOMapKitTurn(@"Continue on Left Bank Road",NAN) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"Continue on Rightbank Road",NAN) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"继续沿左岸路前进",NAN) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"Turn left onto Right Bank Road",NAN) isEqual:@"left"]);
    CHECK([TIOMapKitTurn(@"Continue on Left Bank Road",90) isEqual:@"right"]);
    CHECK([TIOMapKitTurn(@"Head toward destination",NAN) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"继续前进",NAN) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"Continue",0) isEqual:@"unknown"]);
    CHECK([TIOMapKitTurn(@"稍向右转",NAN) isEqual:@"slightRight"]);
    CHECK([TIOMapKitTurn(@"Sharp left",NAN) isEqual:@"sharpLeft"]);
    CHECK([TIOMapKitTurn(@"进入环岛",NAN) isEqual:@"roundabout"]);
    CHECK([TIOMapKitTurn(@"",-90) isEqual:@"left"]);
    CHECK([TIOMapKitTurn(@"直行",NAN) isEqual:@"straight"]);
    CHECK(!TIOMapKitFixValid(NAN,0,5,100,100));
    CHECK(!TIOMapKitFixValid(0,0,-1,100,100));
    CHECK(!TIOMapKitFixValid(0,0,51,100,100));
    CHECK(!TIOMapKitFixValid(0,0,5,84,100));
    CHECK(!TIOMapKitFixValid(0,0,5,103,100));
    CHECK(TIOMapKitFixValid(0,0,5,85,100));
    CHECK([[TIOMapKitProgress alloc] initWithPoints:(id)NSNull.null]==nil);
    CHECK([[TIOMapKitProgress alloc] initWithPoints:@[NSNull.null]]==nil);
    CHECK([[TIOMapKitProgress alloc] initWithPoints:@[@[@YES,@0],@[@0,@1]]]==nil);
    CHECK([[TIOMapKitProgress alloc] initWithPoints:@[@[@0,@0],@[@91,@0]]]==nil);
    CHECK([[TIOMapKitProgress alloc] initWithPoints:@[@[@0,@0],@[@0,@0]]]==nil);
    TIOMapKitProgress *p=[[TIOMapKitProgress alloc]initWithPoints:@[@[@0,@0],@[@0,@0.01],@[@0.01,@0.01],@[@0.01,@0],@[@0,@0],@[@0,@0.01]]];
    NSDictionary *projection=[p projectLatitude:0 longitude:0.001 forwardWindow:150];
    CHECK(fabs([projection[@"progress"] doubleValue]-111.195)<1);
    CHECK([projection[@"distance"] doubleValue]<1);
    [p projectLatitude:0 longitude:0 forwardWindow:150];CHECK(p.progress>110&&p.progress<150);
    [p projectLatitude:0 longitude:0.009 forwardWindow:150];CHECK(p.progress<270);
    CHECK([p projectLatitude:NAN longitude:0 forwardWindow:150]==nil);
    NSArray *window=[p pointsAroundDistance:2200];CHECK(window.count<=128&&window.count>=100);
    CHECK(TIOMapKitDistance(window.firstObject,[p coordinateAtDistance:2100])<0.01);
    CHECK(TIOMapKitDistance(window.lastObject,[p coordinateAtDistance:2650])<0.01);
    CHECK([p pointsAroundDistance:NAN].count==0);
    NSArray *c=[p coordinateAtDistance:55.6];CHECK(fabs([c[1] doubleValue]-0.0005)<0.00001);
    TIOMapKitRerouteGate *gate=[TIOMapKitRerouteGate new];
    CHECK(![gate observeDistance:51 valid:YES uptime:1]);CHECK(![gate observeDistance:60 valid:YES uptime:6]);CHECK([gate observeDistance:60 valid:YES uptime:11]);
    [gate didRequestAt:11];CHECK(![gate observeDistance:60 valid:YES uptime:12]);CHECK(![gate observeDistance:60 valid:YES uptime:17]);CHECK(![gate observeDistance:60 valid:YES uptime:22]);CHECK(![gate observeDistance:60 valid:YES uptime:30]);CHECK([gate observeDistance:60 valid:YES uptime:41]);
    CHECK(![gate observeDistance:60 valid:NO uptime:42]);CHECK(![gate observeDistance:60 valid:YES uptime:43]);CHECK(![gate observeDistance:60 valid:YES uptime:70]);CHECK(![gate observeDistance:60 valid:YES uptime:70]);
    CHECK(![gate observeDistance:50 valid:YES uptime:71]);
    CHECK(![gate observeDistance:60 valid:YES uptime:72]);CHECK(![gate observeDistance:60 valid:YES uptime:82]);CHECK([gate observeDistance:60 valid:YES uptime:83]);
    [gate didRequestAt:83];CHECK(![gate observeDistance:60 valid:YES uptime:84]);CHECK(![gate observeDistance:60 valid:YES uptime:94]);CHECK(![gate observeDistance:60 valid:YES uptime:100]);CHECK([gate observeDistance:60 valid:YES uptime:113]);
    CHECK(![gate observeDistance:INFINITY valid:YES uptime:114]);
    CHECK([p projectLatitude:0 longitude:0 forwardWindow:NAN]==nil);
    printf("MapKitNavigationCoreTests passed\n");return 0;
}}
