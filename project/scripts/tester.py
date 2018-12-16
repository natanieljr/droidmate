#!/usr/bin/python

import sys
import os
import os.path
import shutil
import time

# This file is used for extensive smoke testing of DroidMate.
# You can embed this in your Continuous Integration Pipeline.
# We use https://docs.gitlab.com/ee/user/project/pipelines/schedules.html
# to schedule this as a periodic test, since this test is extensive
# this should not be scheduled with each push.
#
# The script expects to be invoked with a TESTING_REPO and TESTING_SET.
# The script will clone the TESTING_REPO and and will test every .apk
# which is inside the TESTING_REPO/TESTING_SET dir.
# We use scheduled pipeline variables as a flexible approach which will
# be passed to the script, see gitlab-ci.yml.
# Optionally a device to which the script will connect, can be passed
# as further parameter.

TESTING_REPO_CLONE_DIR = "APKResources"
TMP_APK_DIR = "tmp"
COVERAGE_SUFFIX = "-coverage.txt"
ARGS_SUFFIX = ".txt"

# DroidMate constants
DROIDMATE_OUTPUT_DIR = "droidmateout"


def execute(command):
    ret = os.system(command)
    if ret != 0:
        raise ValueError("Expected return value to be equal 0 instead it was %d for the command: %s" % (ret, command))


# TODO think about an approach using the farm.
# Instead of manually connecting the device over adb, call the farm API
def acquire_device(device):
    execute("adb connect %s" % device)


# TODO think about an approach using the farm.
# Instead of manually connecting the device over adb, call the farm API
def release_device(device):
    execute("adb disconnect %s" % device)


def testAPKs(test_dir, droidmate_output_dir):
    tmp_test_dir = os.path.join(test_dir, TMP_APK_DIR)

    apk_files = [f for f in os.listdir(test_dir) if f.lower().endswith(".apk")]
    args_files = [f for f in os.listdir(test_dir) if f.lower().endswith(ARGS_SUFFIX)]
    cov_instr_files = [f for f in os.listdir(test_dir) if f.lower().endswith(COVERAGE_SUFFIX)]

    for apk in apk_files:
        apk_file = os.path.join(test_dir, apk)

        # Setup
        shutil.rmtree(tmp_test_dir, ignore_errors=True)
        os.mkdir(tmp_test_dir)
        shutil.copy(apk_file, tmp_test_dir)

        coverage_args_file = apk + COVERAGE_SUFFIX
        if coverage_args_file in cov_instr_files:
            print("Do coverage instrumentation for %s" % apk)
            f = open(os.path.join(test_dir, coverage_args_file), "r")
            args = f.read()
            execute("./gradlew run --args='--Exploration-apksDir=%s --Output-outputDir=%s %s'"
                    % (tmp_test_dir, droidmate_output_dir, args))

        args = ''
        args_file = apk + ARGS_SUFFIX
        if args_file in args_files:
            f = open(os.path.join(test_dir, args_file), "r")
            args = f.read()

        print("Test %s" % apk)
        execute("./gradlew run --args='--Exploration-apksDir=%s --Output-outputDir=%s %s'"
                % (tmp_test_dir, droidmate_output_dir, args))


def main(testing_repo, testing_set, testing_device):
    print("Tester was called with: TESTING_REPO: %s, TESTING_SET: %s, TESTING_DEVICE: %s"
          % (testing_repo, testing_set, testing_device))

    if testing_device is not None:
        acquire_device(testing_device)

    testing_dir = os.path.abspath(os.path.join("./", TESTING_REPO_CLONE_DIR))
    droidmate_output_dir = os.path.join(testing_dir, DROIDMATE_OUTPUT_DIR)
    try:
        # Setup
        shutil.rmtree(TESTING_REPO_CLONE_DIR, ignore_errors=True)
        execute("git clone %s %s" % (testing_repo, testing_dir))
        execute("./gradlew clean build")
        execute("./gradlew build -x test")

        # Test
        testAPKs(os.path.join(testing_dir, testing_set), droidmate_output_dir)

    finally:
        # Cleanup
        shutil.rmtree(testing_dir, ignore_errors=True)
        # One could also keep it for further analysis
        shutil.rmtree(droidmate_output_dir, ignore_errors=True)
        if testing_device is not None:
            release_device(testing_device)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        raise ValueError("Expected at least TESTING_REPO and TESTING_SET to be passed.")
    start_time = time.time()
    testing_device = sys.argv[3] if len(sys.argv) == 4 else None
    main(sys.argv[1], sys.argv[2], testing_device)
    end_time = time.time()
    print("The testing took: %d sec" % (end_time - start_time))
