// Existing native bridge: only owned route text can bypass the foreground gate.
static BOOL SubtitleCanSend(NSUInteger type,BOOL foreground){
    return type==3||foreground||(type==5&&Trial.routeScoped&&RouteCanContinue());
}
