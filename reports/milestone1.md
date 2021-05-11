# Milestone 1 Submission

## Application Description: 
```bash
# Please describe the application you want to work on here
```

## Software Implementation: 
```bash
The software implementation can be found at: https://github.com/PhilipPfeffer/sha_hash/blob/main/sha.py
```

## Setup Instructions:
```bash
There are three modes on our software demo:

- If you wish to run a simple demo with a hard-coded string, simply call "python3 sha.py"
- If you wish to run the script with your own string, run "python3 sha.py -s yoursamplestring"
- If you wish to run the script with a sample .txt file, run "python3 sha.py -f yourfile.txt". We added bible.txt and small_bible.txt to the repo if you wish to try the script out with those files.

```

## Performance Analysis:
```bash
# Please add a performance analysis of your software implementation. 
# This can be an analytical analysis of the computations or from a profiling package (e.g. Tensorboard).

# Here's a list of instrumentation / profiling tools:
# C/C++: Valgrind, Callgrind.
# Python: trace, timeit
# TensorFlow: TensorBoard
# PyTorch: torch.utils.bottleneck, etc.
# CUDA: CUDA::Profiler
```
We used the trace and time Python modules to evaluate bottlenecks in our software implementation. We split the code into several sections and evaluated how many times that section was called and how long it took to complete as a multiple of the fastest section. reports/sha.cover shows how many times each line/function is called.

Section | Slower Factor
0 Slower by factor of 6.887108792844754
1 Slower by factor of 2.7237953303525013
    2 Slower by factor of 19.888412816687353
    <!-- 3 Slower by factor of 228.8770491802875 -->
    4 Slower by factor of 27.172006954788586
5 Slower by factor of 1234.2800546445872
6 Slower by factor of 1.0


Not that section 5 comprises sections 2 and 4 run several times, hence the indent. Section 3 is not descriptive because it records the time difference between different loop iterations.

Despite only being run once, section 1, message_parsing, took a long time. When we write code to parse every chunk of a larger message, this will become a more serious problem. This bottleneck can be refactored out of this loop body and done as preprocessing.

We also note that sections 2 and 4 are very slow. These describe the maths operations of the hashing algorithm. Although they appear to be slow, we believe this may be becuase Python does not handle bit operations simply. Therefore, many helper functions are called - in some cases, thousands of times. This will not be a problem in our Spatial implementation, where we can directly do bit operations.
