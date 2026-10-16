#import <Foundation/Foundation.h>
#import "SchemaView.h"

typedef NS_ENUM(NSUInteger, TIOSchemaJSONStatus) {
    TIOSchemaJSONOK,
    TIOSchemaJSONInvalidBytes,
    TIOSchemaJSONInvalidUTF8,
    TIOSchemaJSONInvalidSyntax,
    TIOSchemaJSONLimitExceeded,
    TIOSchemaJSONUnverifiedShape,
    TIOSchemaJSONUnsupportedPolicy,
    TIOSchemaJSONDuplicateKey,
    TIOSchemaJSONUnexpectedShape
};

/* Input must be the owned copy returned by SchemaView, never an opaque engine object.
 * Returns count/type metadata only. Errors never contain input bytes or parser details. */
FOUNDATION_EXPORT NSDictionary<NSString *, id> *TIOSchemaDecodeMetadata(
    const TIOSchemaOwnedBytes *owned, TIOSchemaJSONStatus *status);

/* Explicit opt-in canonical schema metadata only; never record contents.
 * Input must already be an owned entry-time copy. No file/network output occurs. */
FOUNDATION_EXPORT NSDictionary<NSString *, id> *TIOSchemaDecodeProjection(
    const TIOSchemaOwnedBytes *owned, TIOSchemaJSONStatus *status);
