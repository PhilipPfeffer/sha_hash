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
| NUM_CHUNKS |     Cycles        | % difference from previous
|    1       | 18264 cycles/iter |          0%
|    2       | 13873 cycles/iter |        -24.04%
|    4       | 11909 cycles/iter |        -14.15%
|    8       | 10977 cycles/iter |        -7.83%


| LARGE_NUM_CHUNKS | NUM_CHUNKS |     Cycles        |
|        1         |    4       | 11920 cycles/iter |        
|        1         |    8       | 11392 cycles/iter |
|        2         |    4       | 12020 cycles/iter |
|        2         |    8       | 11320 cycles/iter |



Preprocessing task: Controller Tree Info
2 chunks - 839 cycles/iter * 5 iters = 4199 total cycles
4 chunks - 1409 cycles/iter * 3 iters = 4227 total cycles
8 chunks - 2144 cycles/iter * 1 iters = 4289 total cycles