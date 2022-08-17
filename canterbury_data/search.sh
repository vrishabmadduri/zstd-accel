#!/bin/bash

touch my.log
./lzbench -resnappy ../silesia/$1 > my.log &
# run binary search on zstd/zstd_fast
# edit config file for to reach further negative levels of zstd
#./lzbench -rezstd_fast,-500 ../silesia/$1 > my.log &
#./lzbench -rezstd,22 ../silesia/$1 > my.log &
BACK_PID=$!

while kill -0 $BACK_PID ; do
    echo "Process is still active..."
    tr -d '\015' <my.log >file2
    mv file2 my.log
    sed 's/\s\s\s\s\s/\n/g' my.log > newlog.log
    sed '/iter/d' newlog.log
    tr '\n' ' ' < new.log | sed 's/snappy 2020-07-11/\n&/g'
    sed 's/\x-ray.*/x-ray/' new1.log > new2.log
    sed 's/\Filename.*/Filename/' new2.log > new3.log
    rm -rf my.log newlog.log new.log new1.log new2.log
    python search_level.py
done
