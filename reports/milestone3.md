# Milestone 3 Submission
In milestone 2, you have already developed your application in Spatial and verified that it was functionally correct. However, system architecture design is all about getting the correct results with the lowest latency and resource utilization. In this milestone, your task will be to tune your implementation such that the hardware resource uitilization is reasonable while keeping the same result as in the simulation. Please state the optimization you have done to maintain or improve the performance while keeping the resource utilization reasonable. You can follow the instructions in the submission file.


## Performance numbers
In this section, please attach the resource utilization and the cycle count of your application when running on the board: 
```bash 
# Please attach your report here

```

## Useful Info (preliminary - to use for final report)
 NUM_CHUNKS |     Cycles        | % difference from previous
    1       | 18264 cycles/iter |          0%
    2       | 13873 cycles/iter |        -24.04%
    4       | 11909 cycles/iter |        -14.15%
    8       | 10977 cycles/iter |        -7.83%


Preprocessing task: Controller Tree Info
2 chunks - 839 cycles/iter * 5 iters = 4199 total cycles
4 chunks - 1409 cycles/iter * 3 iters = 4227 total cycles
8 chunks - 2144 cycles/iter * 1 iters = 4289 total cycles


## Design Choices: What design choices have you made to implement the hardware implementation? 
The optimisation we have focused on for this milestone is the data preprocessing portion. The implementation was changed with the described design choices as follows:
1. First, each 64-byte chunk of data was being loaded and then preprocessed sequentially 
    (i.e. Sequential.Foreach(... par NUM_CHUNKS){ load; preprocess}). 

2. Second, we parallelised the loading of each 64-byte chunk of data and its preprocessing 
    (i.e. Foreach(... par NUM_CHUNKS){ load; preprocess}). 
    This approach limits the size of NUM_CHUNKS to 2 because there are only 4 data loaders on the FPGA (trying the boundary case of NUM_CHUNKS=4 did not work).

3. Third, our current approach, loads a larger (64*NUM_CHUNKS-byte) contiguous chunk of data in one go 
    (i.e. Sequential.Foreach() { load big chunk; Foreach(... par NUM_CHUNKS){ preprocess }}). 
    Then, each 64-byte chunk (of which there are NUM_CHUNKS chunks) is being preprocessed in parallel. (Note that each chunk must be pre-processed sequentially, but that we are processing multiple chunks in parallel). Here, we show the tradeoff for different values of NUM_CHUNKS=[1,2,4,8].

4. A futher optimisation we would like to try is to first load as much data into SRAM as possible (rather than 64*NUM_CHUNKS-bytes). 
    (i.e. Sequential.Foreach() { load biggest chunk possible; Sequential.Foreach(){ Foreach(... par NUM_CHUNKS){ preprocess }} })
    We are limited on the parallelisation of the preprocessing by logic utilisation to NUM_CHUNKS=TODO. However, this does not limit the amount of data we can load. Therefore, we can load as much data as possible first, and then parallely process NUM_CHUNKS 64-byte chunks. This reduces the amount of time spent loading data into SRAM.
