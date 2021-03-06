#!/bin/bash

set -e

cd git*/tink

echo "========================================================= Running cmake"
cmake --version
cmake . -DTINK_BUILD_TESTS=ON
echo "==================================================== Building with make"
make -j8 all
echo "===================================================== Testing with make"
CTEST_OUTPUT_ON_FAILURE=1 make test
echo "================================================ Done testing with make"

export TINK_SRC_DIR=$(pwd)
export TEST_TMPDIR=$(mktemp -d)
export TEST_SRCDIR=$(cd ..; pwd)
cd examples/helloworld/cc
./cmake_build_test.sh
