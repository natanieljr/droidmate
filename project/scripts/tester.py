#!/usr/bin/python

import sys
import os
import os.path
import shutil
import time
from test_device import *

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
# Optionally, the following parameter can be passed:
# - DEVICE_SERIAL: A device serial number.
# - FARM_ADDRESS: A URL for an OpenSTF farm.
# - FARM_AUTH_TOKEN: An authentication token, which is needed for the farm
#                    communication.
# If all these optional parameters are passed, the script will try to
# acquire the device via the OpenSTF farm.

TESTING_REPO_CLONE_DIR = "APKResources"
TMP_APK_DIR = "tmp"
COVERAGE_SUFFIX = "-coverage.txt"
ARGS_SUFFIX = ".txt"

DEVICES = [Emulator("emu23"), Emulator("emu24"), Emulator("emu27")]

# DroidMate constants
DROIDMATE_OUTPUT_DIR = "droidmateout"


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


def main(testing_repo, testing_set, device_serial, farm_address, farm_auth_token):
    print("Tester was called with: TESTING_REPO: %s, TESTING_SET: %s, DEVICE_SERIAL: %s, FARM_ADDRESS: %s, FARM_AUTH_TOKEN: %s"
          % (testing_repo, testing_set, device_serial, farm_address, farm_auth_token))

    if device_serial is not None:
        assert farm_address is not None
        assert farm_auth_token is not None
        DEVICES.append(FarmDevice(device_serial, farm_address, farm_auth_token))

    testing_dir = os.path.abspath(os.path.join("./", TESTING_REPO_CLONE_DIR))
    droidmate_output_dir = os.path.join(testing_dir, DROIDMATE_OUTPUT_DIR)
    try:
        # Setup
        shutil.rmtree(TESTING_REPO_CLONE_DIR, ignore_errors=True)
        execute("git clone %s %s" % (testing_repo, testing_dir))
        execute("./gradlew build")

        for device in DEVICES:
            try:
                execute("adb devices")

                # Setup and connect to the device
                device.acquire_device()

                execute("adb devices")
                # Test
                testAPKs(os.path.join(testing_dir, testing_set), droidmate_output_dir)
            except Exception as err:
                print("An exception happened:  " + str(err))
                raise err
            finally:
                # Release device
                device.release_device()
    except Exception as err:
        print("An exception happened:  " + str(err))
        raise err
    finally:
        # Cleanup
        shutil.rmtree(testing_dir, ignore_errors=True)
        # One could also keep it for further analysis
        shutil.rmtree(droidmate_output_dir, ignore_errors=True)


if __name__ == "__main__":
    if len(sys.argv) != 3 and len(sys.argv) != 6:
        raise ValueError("Expected at least TESTING_REPO and TESTING_SET to be passed."
                         "tester.py $RESOURCE_REPO $TESTING_SET [$DEVICE_SERIAL $FARM_ADDRESS $FARM_AUTH_TOKEN]")

    start_time = time.time()

    device_serial = sys.argv[3] if len(sys.argv) == 6 else None
    farm_address = sys.argv[4] if len(sys.argv) == 6 else None
    farm_auth_token = sys.argv[5] if len(sys.argv) == 6 else None

    main(sys.argv[1], sys.argv[2], device_serial, farm_address, farm_auth_token)
    end_time = time.time()
    print("The testing took: %d sec" % (end_time - start_time))
