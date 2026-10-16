#include "ImageAttestation.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
 TIOImageBudget b; TIOImageBudgetInit(&b); TIOImageProof p;
 const unsigned char invalid[32]={0};
 assert(TIOImageAttestStatic(TIOImageFlutter,invalid,sizeof invalid,&b,&p)!=TIOImageOK);
 assert(!p.content_verified && !p.loaded_identity_verified && !p.slot_metadata_verified);
 puts("image attestation rejects tainted manifest/header PASS");
 return 0;
}
