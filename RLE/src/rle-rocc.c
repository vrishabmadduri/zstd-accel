#include "rocc.h"


static inline void rle_stage(unsigned long data, void *ptr) {
    ROCC_INSTRUCTION_SS(0, data, (uintptr_t) ptr, 0);
}

static inline unsigned long rle_encode(void *ptr) {
    unsigned long data;
    asm volatile ("fence");
    ROCC_INSTRUCTION_DS(0, data, (uintptr_t) ptr, 1);
    asm volatile ("fence");
    return data;
}

// rle_decode

unsigned long data = 0x616162626363;

int main(void) {
    // write some test
    unsigned long result;
    unsigned long answer = 0x326132623263;
    result = rle_encode(&data);
    if(result != answer)
        return -1
    return 1
}