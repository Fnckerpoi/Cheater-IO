#import "OfficialImportTrialCore.h"
FOUNDATION_EXPORT TIOImportTrialCore *TIOOfficialImportTrialCore(void);
FOUNDATION_EXPORT BOOL TIOOfficialImportTrialArm(BOOL functional);
FOUNDATION_EXPORT void TIOOfficialImportTrialStop(NSString *reason);
FOUNDATION_EXPORT NSString *TIOOfficialImportTrialHostStatus(void);
#if TIO_IMPORT_TRIAL_TESTING
FOUNDATION_EXPORT BOOL TIOImportTrialTestBind(Class cls,TIOImportTrialCore *core);
#endif
