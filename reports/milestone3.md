# Milestone 3 Submission

## Performance numbers
In this section, please attach the resource utilization and the cycle count of your application when running on the board: 
Resource utilisation for NUM_CHUNKS=8.
```bash 
+--------------------------------------+-------+-------+-----------+-------+
|               Site Type              |  Used | Fixed | Available | Util% |
+--------------------------------------+-------+-------+-----------+-------+
| Slice LUTs                           | 89554 |     0 |    218600 | 40.97 |
|   LUT as Logic                       | 74362 |     0 |    218600 | 34.02 |
|   LUT as Memory                      |  2403 |     0 |     70400 |  3.41 |
|     LUT as Distributed RAM           |   400 |     0 |           |       |
|     LUT as Shift Register            |  2003 |     0 |           |       |
|   LUT used exclusively as pack-thrus | 12789 |     0 |    218600 |  5.85 |
| Slice Registers                      | 57346 |     0 |    437200 | 13.12 |
|   Register as Flip Flop              | 57346 |     0 |    437200 | 13.12 |
|   Register as Latch                  |     0 |     0 |    437200 |  0.00 |
|   Register as pack-thrus             |     0 |     0 |    437200 |  0.00 |
| F7 Muxes                             |  1408 |     0 |    109300 |  1.29 |
| F8 Muxes                             |   284 |     0 |     54650 |  0.52 |
+--------------------------------------+-------+-------+-----------+-------+
```

## Performance Tuning
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
    We are limited on the parallelisation of the preprocessing by logic utilisation to NUM_CHUNKS=8 (we have not been able to get NUM_CHUNKS=16 to work). However, this does not limit the amount of data we can load. Therefore, we can load as much data as possible first, and then parallely process NUM_CHUNKS 64-byte chunks. This reduces the amount of time spent loading data into SRAM.
