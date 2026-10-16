#include "SchemaPolicy.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
static bool valid(const char*s){return TIOCaptureValidateSchema((const unsigned char*)s,strlen(s));}
int main(void){assert(valid("[]"));assert(valid("[{\"name\":\"P\",\"properties\":[{\"name\":\"password\",\"type\":\"String\"}]}]"));const char*bad[]={"[] garbage","{}","[{\"name\":\"A\",\"na\\u006de\":\"B\",\"properties\":[]}]","[{\"name\":\"P\",\"records\":[],\"properties\":[]}]","[{\"name\":\"P\",\"properties\":[{\"name\":\"a\",\"type\":\"String\",\"enumMap\":{}}]}]","[{\"name\":\"P\\u0000X\",\"properties\":[]}]","[{\"name\":\"P\",\"properties\":[{\"type\":\"Object\",\"target\":\"Missing\"}]}]"};for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)assert(!valid(bad[i]));unsigned char utf8[]={0xc0,0xaf};assert(!TIOCaptureValidateSchema(utf8,2));size_t n=0;const char*input=" [ { \"properties\" : [ ], \"name\" : \"P\" } ] ";unsigned char*canonical=TIOCaptureCanonicalSchema((const unsigned char*)input,strlen(input),&n);assert(canonical&&n==strlen("[{\"name\":\"P\",\"properties\":[]}]"));assert(!memcmp(canonical,"[{\"name\":\"P\",\"properties\":[]}]",n));free(canonical);puts("PURE_C_SCHEMA_POLICY_PASS");return 0;}
