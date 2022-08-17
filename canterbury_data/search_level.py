import os
import re

# ./lzbench -resnappy/zstd_fast ../silesia/dickens > my.log
#tr -d '\015' <my.log >file2
#mv file2 my.log
#sed 's/\s\s\s\s\s/\n/g' my.log > newlog.log
#sed '/iter/d' newlog.log
#tr '\n' ' ' < new.log | sed 's/snappy 2020-07-11/\n&/g'
#sed 's/\x-ray.*/x-ray/' new1.log > new2.log
#sed 's/\Filename.*/Filename/' new2.log > new3.log
# rm -rf my.log newlog.log new.log new1.log new2.log

f = open("new3.log", "r")
for line in f:
    match = re.match('snappy', line)
    if match:
        snappy_compression_throughput = re.match('(\d*)', line).group(0)
        snappy_compression_ratio = re.match('(\d*)', line).group(2)
    else:
        zstd_compression_throughput = re.match('(\d*)', line).group(0)
        zstd_compression_ratio = re.match('(\d*)', line).group(2)

        if (zstd_compression_throughput > snappy_compression_throughput) and (zstd_compression_ratio > snappy_compression_ratio):
            print(re.match('zstd_fast 1.5.0 --.', line).group(0))
