#include "rocc.h"
#include "stdint.h"

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

unsigned long data = 0x000000;

int main(void) {
    // write some test
    unsigned long result;
    unsigned long answer = 0x600;
    rle_stage(3, &data);
    result = rle_encode(&result);
    if(result != answer)
        return -1;
    return 0;
}
