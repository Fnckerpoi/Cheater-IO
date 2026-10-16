#import <Foundation/Foundation.h>
#import "HostCompatibility.h"
#include <assert.h>
struct Image {struct mach_header_64 header;struct uuid_command uuid;};
static struct Image ImageFor(NSString *uuid) {
    struct Image image={0};image.header.magic=MH_MAGIC_64;image.header.filetype=MH_EXECUTE;image.header.ncmds=1;image.header.sizeofcmds=sizeof(struct uuid_command);image.uuid.cmd=LC_UUID;image.uuid.cmdsize=sizeof(struct uuid_command);
    [[[NSUUID alloc]initWithUUIDString:uuid] getUUIDBytes:image.uuid.uuid];return image;
}
int main(void) {@autoreleasepool {
    NSArray *cases=@[@[@"1.0.2",@"67",@"EEEA85E5-4114-313C-B651-73C90A6B5D3C"],@[@"1.0.4",@"195",@"261C8E78-F955-3D7D-85F7-13B9082972CF"],@[@"1.0.5",@"201",@"748FD301-DA60-3095-A244-9BD0558FACA5"]];
    for(NSArray *row in cases){NSDictionary *info=@{@"CFBundleShortVersionString":row[0],@"CFBundleVersion":row[1]};struct Image image=ImageFor(row[2]);assert(TIOHostImageMatches((const void *)&image,info));image.uuid.uuid[0]^=1;assert(!TIOHostImageMatches((const void *)&image,info));}
    struct Image image=ImageFor(@"748FD301-DA60-3095-A244-9BD0558FACA5");assert(!TIOHostImageMatches((const void *)&image,@{@"CFBundleShortVersionString":@"1.0.6",@"CFBundleVersion":@"218"}));assert(!TIOHostImageMatches((const void *)&image,@{@"CFBundleShortVersionString":@"1.0.5",@"CFBundleVersion":@"218"}));assert(!TIOHostImageMatches(NULL,@{}));
    puts("ORIGINAL_HOST_VERSION_UUID_GATE_PASS");
}return 0;}
