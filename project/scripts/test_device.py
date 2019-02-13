#!/usr/bin/python

import contextlib
import json
import os
import signal
import subprocess
import sys
import time
import urllib2
import urllib
import urlparse
import pdb


# Some functions are copied from: https://github.com/servo/servo/blob/master/etc/run_in_headless_android_emulator.py

ANDROID_SDK_HOME_ENV_NAME = "ANDROID_SDK_HOME"
DEBUG = True


class NoDeviceException(Exception):
    pass


def debugprint(msg):
    if DEBUG:
        print(msg)
        sys.stdout.flush()


def execute(command):
    debugprint("Execute: " + command)
    ret = os.system(command)
    if ret != 0:
        raise ValueError("Expected return value to be equal 0 instead it was %d for the command: %s" % (ret, command))


def execute_as_process(*args, **kwargs):
    return subprocess.Popen(*args, **kwargs)


def terminate_process(process):
    if process.poll() is None:
        debugprint("Terminate process")
        # The process seems to be still running
        process.terminate()


@contextlib.contextmanager
def terminate_on_exit(*args, **kwargs):
    process = subprocess.Popen(*args, **kwargs)
    try:
        yield process
    finally:
        if process.poll() is None:
            debugprint("Terminate process")
            # The process seems to be still running
            process.terminate()


def get_adb_path():
    if ANDROID_SDK_HOME_ENV_NAME in os.environ:
        return os.path.join(os.environ[ANDROID_SDK_HOME_ENV_NAME], "platform-tools", "adb")
    else:
        raise ValueErrror("Could not find %s" % ANDROID_SDK_HOME_ENV_NAME)


def get_emulator_path():
    if ANDROID_SDK_HOME_ENV_NAME in os.environ:
        return os.path.join(os.environ[ANDROID_SDK_HOME_ENV_NAME], "emulator", "emulator")
    else:
        raise ValueErrror("Could not find %s" % ANDROID_SDK_HOME_ENV_NAME)


def wait_for_boot(adb):
    while 1:
        with terminate_on_exit(
            adb + ["shell", "getprop", "sys.boot_completed"],
            stdout=subprocess.PIPE,
        ) as getprop:
            stdout, stderr = getprop.communicate()
            if "1" in stdout:
                return
        time.sleep(1)


class Emulator:
    def __init__(self, name):
        self.name = name
        self.emulatorport = "5580"
        self.emulatorargs = ["-no-boot-anim", "-no-window", "-no-audio", "-gpu", "off", "-no-snapshot-save", "-wipe-data", "-accel", "on"]
        self.emulatorprocess = None

    # /android-sdk/emulator/emulator -avd emu23 -no-boot-anim -no-window -no-audio -gpu off -no-snapshot-save -wipe-data -accel on
    def acquire_device(self):
        debugprint("Acquire device")
        self.emulatorprocess = execute_as_process([get_emulator_path(), "-avd", self.name, "-port", self.emulatorport] + self.emulatorargs, stdout=sys.stderr)

        # This is hopefully enough time for the emulator to exit
        # if it cannot start because of a configuration problem,
        # and probably more time than it needs to boot anyway
        time.sleep(4)

        if self.emulatorprocess is None or self.emulatorprocess.poll() is not None:
            # The emulator process has terminated already,
            # wait-for-device would block indefinitely
            raise ValueError("Emulator did not start")

        adb_path = get_adb_path()
        adb_device = [adb_path, "-s", "emulator-" + self.emulatorport]
        with terminate_on_exit(adb_device + ["wait-for-device"]) as wait_for_device:
            wait_for_device.wait()

        wait_for_boot(adb_device)

    def release_device(self):
        debugprint("Release device")
        terminate_process(self.emulatorprocess)


class PhysicalDevice:
    def __init__(self, serial):
        self.serial = serial

    def acquire_device(self):
        execute("adb connect %s" % self.serial)

    def release_device(self):
        execute("adb disconnect %s" % self.serial)


class FarmDevice:
    def __init__(self, device_serial, farm_address, farm_auth_token):
        self.device_serial = device_serial
        self.farm_address = farm_address
        self.farm_auth_token = farm_auth_token
        self.remote_connect_url = None

    def acquire_device(self):
        self.acquire_authentication()
        self.connect_to_remote_device()

    def acquire_authentication(self):
        url = urlparse.urljoin(self.farm_address, '/api/v1/user/devices')
        data = {"serial": self.device_serial}
        headers = {'content-type': 'application/json', 'Authorization': 'Bearer ' + self.farm_auth_token}

        try:
            req = urllib2.Request(url, json.dumps(data), headers)
            response = urllib2.urlopen(req).read()
        except urllib2.HTTPError as err:
            msg = "Device already in use. Could not acquire device from farm." \
                if err.code == 403 \
                else "Could not acquire device from farm. " + err.msg
            raise NoDeviceException(msg)

    def connect_to_remote_device(self):
        url = urlparse.urljoin(self.farm_address, '/api/v1/user/devices/' + self.device_serial + '/remoteConnect')
        headers = {'Authorization': 'Bearer ' + self.farm_auth_token}

        try:
            req = urllib2.Request(url, headers=headers)
            req.get_method = lambda: 'POST'
            response = urllib2.urlopen(req).read()
            response_json = json.loads(response)
            self.remote_connect_url = response_json['remoteConnectUrl']
            execute('adb connect ' + self.remote_connect_url)

        except urllib2.HTTPError as err:
            msg = "Could not get remote address. " + err.msg
            raise NoDeviceException(msg)
        pass

    def release_device(self):
        if self.remote_connect_url is not None:
            execute('adb disconnect ' + self.remote_connect_url)

        # Disconnect a remote debugging session
        url = urlparse.urljoin(self.farm_address, '/api/v1/user/devices/' + self.device_serial + '/remoteConnect')
        opener = urllib2.build_opener(urllib2.HTTPHandler)
        request = urllib2.Request(url)
        request.add_header('Authorization', 'Bearer ' + self.farm_auth_token)
        request.get_method = lambda: 'DELETE'
        response = opener.open(request)

        # Removes a device from the authenticated user's device list.
        # This is analogous to pressing "Stop using" in the UI
        url = urlparse.urljoin(self.farm_address, '/api/v1/user/devices/' + self.device_serial)
        opener = urllib2.build_opener(urllib2.HTTPHandler)
        request = urllib2.Request(url)
        request.add_header('Authorization', 'Bearer ' + self.farm_auth_token)
        request.get_method = lambda: 'DELETE'
        response = opener.open(request)
