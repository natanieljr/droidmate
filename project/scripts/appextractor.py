#!/usr/bin/env python3

import os
import sys
from bs4 import BeautifulSoup
import urllib.request
import ssl

# This script downloads according to number_apps_per_category apps per category
# by the specified categories. The categories could be changed by Google so they
# might be updated once in a while.
# This script is useful for experiments needing representative number of apps
# with a representative distribution.

number_apps_per_category = 6

# One could also parse the categories from the Google play store
categories = [
    "AUTO_AND_VEHICLES",
    "BEAUTY",
    "BOOKS_AND_REFERENCE",
    "BUSINESS",
    "COMICS",
    "DATING",
    "PRODUCTIVITY",
    "PARENTING",
    "FOOD_AND_DRINK",
    "FINANCE",
    "PHOTOGRAPHY",
    "HEALTH_AND_FITNESS",
    "HOUSE_AND_HOME",
    "MAPS_AND_NAVIGATION",
    "COMMUNICATION",
    "ART_AND_DESIGN",
    "EDUCATION",
    "LIFESTYLE",
    "MEDICAL",
    "MUSIC_AND_AUDIO",
    "NEWS_AND_MAGAZINES",
    "PERSONALIZATION",
    "TRAVEL_AND_LOCAL",
    "SHOPPING",
    # "LIBRARIES_AND_DEMO",
    "SOCIAL",
    "SPORTS",
    "EVENTS",
    "TOOLS",
    "ENTERTAINMENT",
    "VIDEO_PLAYERS",
    "ANDROID_WEAR",
    "WEATHER",
    "GAME",
    # "GAME_ADVENTURE",
    # "GAME_ACTION",
    # "GAME_ARCADE",
    # "GAME_BOARD",
    # "GAME_CASINO",
    # "GAME_PUZZLE",
    # "GAME_CASUAL",
    # "GAME_CARD",
    # "GAME_EDUCATIONAL",
    # "GAME_MUSIC",
    # "GAME_TRIVIA",
    # "GAME_RACING",
    # "GAME_ROLE_PLAYING",
    # "GAME_SIMULATION",
    # "GAME_SPORTS",
    # "GAME_STRATEGY",
    # "GAME_WORD",
    "FAMILY",
    # "FAMILY?age=AGE_RANGE1",
    # "FAMILY?age=AGE_RANGE2",
    # "FAMILY?age=AGE_RANGE3",
    # "FAMILY_ACTION",
    # "FAMILY_EDUCATION",
    # "FAMILY_BRAINGAMES",
    # "FAMILY_CREATE",
    # "FAMILY_MUSICVIDEO",
    # "FAMILY_PRETEND"
              ]

user_agent = 'Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.0.7) Gecko/2009021910 Firefox/3.0.7'
context = ssl._create_unverified_context()
ssl._create_default_https_context = ssl._create_unverified_context
headers = {'User-Agent': user_agent}
opener = urllib.request.build_opener()
opener.addheaders = [('User-agent', user_agent)]
urllib.request.install_opener(opener)


def play_store_topselling_free_url(category):
    return f"https://play.google.com/store/apps/category/{category}/collection/topselling_free"


def get_html_from_url(url):
    request = urllib.request.Request(url, None, headers)
    fp = urllib.request.urlopen(request, context=context)

    mybytes = fp.read()
    page = mybytes.decode("utf8")
    fp.close()

    return page


def download_file_from_url(url, download_location, file_name):
    urllib.request.urlretrieve(url, os.path.join(download_location, file_name))


def download_app(app, category, download_location):
    apk_path = None
    # Search for the app
    store_base_url = "https://apkpure.com"
    try:
        apkpure_search = get_html_from_url(f"{store_base_url}/search?q={app}&t=app")
        soup = BeautifulSoup(apkpure_search, "lxml")
        apps = soup.body.find_all(attrs={"class": "search-title"})
        if len(apps) <= 0:
            print(f"Could not download: {app}")
            return apk_path
        href = (apps[0]).find(attrs={"href": True})['href']

        # Follow the first link from the search
        url = f"{store_base_url}{href}/download?from=details"
        app_page = get_html_from_url(url)
        soup = BeautifulSoup(app_page, "lxml")
        download_elem = soup.body.find(attrs={"id": "download_link"})
        if download_elem is None:
            print(f"Could not download: {app}")
            return apk_path
        download_link = download_elem['href']
        apk_file_name = f"{category}_{app}.apk"
        download_file_from_url(download_link, download_location, apk_file_name)
        return os.path.join(download_location, apk_file_name)
    except:
        print("Unexpected error:", sys.exc_info()[0])
        print(f"Could not download: {app}")
        return apk_path


def download_apps(app_ids, download_location):
    for (app, category) in app_ids:
        download_app(app, category, download_location)


def get_app_ids_from_play_store_url(url, number, category):
    page = get_html_from_url(url)
    soup = BeautifulSoup(page, "lxml")
    apps = soup.body.find_all(attrs={"data-docid":True, "class":"card no-rationale square-cover apps small"})
    ids = []
    for app in apps[:number]:
        id = app["data-docid"]
        ids.append((id, category))
        print(id)

    return ids


def main(download_location="./"):
    for category in categories:
        print(f"-------------------Category: {category}")
        url = play_store_topselling_free_url(category)
        app_ids = get_app_ids_from_play_store_url(url, number_apps_per_category, category)
        download_apps(app_ids, download_location)


if __name__ == "__main__":
    # Specify here your desired output location, otherwise the current directory will be used
    main()
