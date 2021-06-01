# Milestone 3 Submission
In milestone 2, you have already developed your application in Spatial and verified that it was functionally correct. However, system architecture design is all about getting the correct results with the lowest latency and resource utilization. In this milestone, your task will be to tune your implementation such that the hardware resource uitilization is reasonable while keeping the same result as in the simulation. Please state the optimization you have done to maintain or improve the performance while keeping the resource utilization reasonable. You can follow the instructions in the submission file.


## Performance numbers
In this section, please attach the resource utilization and the cycle count of your application when running on the board: 
```bash 
# Please attach your report here

```

## Useful Info (preliminary - to use for final report)
2 chunks - 13873 cycles/iter
4 chunks - 11909 cycles/iter (-14.15%)
8 chunks - 10977 cycles/iter (-7.83%)


Preprocessing task
2 chunks - 839 cycles/iter * 5 iters = 4199 total cycles
4 chunks - 1409 cycles/iter * 3 iters = 4227 total cycles
8 chunks - 2144 cycles/iter * 1 iters = 4289 total cycles


## Design Choices
What design choices have you made to implement the hardware implementation? 

