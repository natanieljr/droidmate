#!/usr/bin/env python3

import os
import sys
import shutil
from appextractor import get_app_ids_from_play_store_url
from appextractor import play_store_topselling_free_url
from appextractor import download_app
from appextractor import categories
import csv
import statistics
import matplotlib.pyplot as plot
# pip install gitpython
from git import Repo

# This script downloads according to number_apps_per_category apps per category
# by the specified categories.
# DroidMate will be executed for each successfully downloaded app accordingly to
# the defined num_droidmate_actions and droidmate_path. Afterwards the model
# directory is checked for encountered widgets. This raw data is written into
# results_file_name.
# In the end statistical information about all the acquired raw data is written
# into summary_file_name.
# Overall, the script is useful to automatically analyze the current top free apps
# from the Google Play Store and create statistics. In particular, by analyzing
# the number of encountered widgets with DroidMate, we gain a distribution of
# how many apps are explorable by DroidMate, i.e. we can access the widget information
# because e.g. Unity is not used.

# General config
results_file_name = "results.csv"
summary_file_name = "summary.txt"
# The desired number of apps, that are supposed to be downloaded and afterwards
# be tested.
number_apps_per_category = 6

# DroidMate run config
num_droidmate_actions = 7

# Either use local DroidMate or clone from url
# Adjust this path, if you want to use your local DroidMate
droidmate_path = ""
droidmate_repository = "git@github.com:uds-se/droidmate.git"

# CSV
category_str = "Category"
nr_of_widgets_str = "Nr. of widgets"
apk_id_str = "ApkId"
unique_id_str = "Unique Id"


def execute(command):
    print(f"Execute: {command}")
    ret = os.system(command)
    if ret != 0:
        raise ValueError("Expected return value to be equal 0 instead it was %d for the command: %s" % (ret, command))


def print_err(msg):
    sys.stderr.write(msg)
    sys.stderr.flush()


def clone_repository(repo_url, dm_path):
    Repo.clone_from(repo_url, dm_path)


def recreate_dir_safely(direc):
    shutil.rmtree(direc, ignore_errors=True)
    os.mkdir(direc)


def setup(destination_dir, repository):
    # Either use local DroidMate or clone via repository
    dm_path = droidmate_path
    # dm_path = os.path.join(destination_dir, "droidmate")
    # clone_repository(repository, dm_path)

    summary_file_path = os.path.join(destination_dir, summary_file_name)
    results_file_path = os.path.join(destination_dir, results_file_name)
    if os.path.isfile(results_file_path):
        pass
    else:
        f = open(results_file_path, "x")
        f.write(f"{category_str};{apk_id_str};{nr_of_widgets_str}\n")
        f.close()
    return (results_file_path, summary_file_path, dm_path)


def eval_results(summary_file_path, results_file_path):
    widget_counts = []
    category_widget_counts_map = {}
    with open(results_file_path, newline='') as results_file:
        spamreader = csv.DictReader(results_file, delimiter=';')
        for row in spamreader:
            w_views = int(row[nr_of_widgets_str])
            widget_counts.append(w_views)

            cat = row[category_str]
            if cat not in category_widget_counts_map:
                category_widget_counts_map[cat] = []
            category_widget_counts_map[cat].append(w_views)

    with open(summary_file_path, newline="", mode="a") as summary_file:
        # DroidMate config
        summary_file.write(f"DroidMate config:\n")
        summary_file.write(f"actionLimit={num_droidmate_actions}\n\n")

        # General statistics
        avg = statistics.mean(widget_counts)
        summary_file.write(f"Average number of unique widgets per app:\n{avg}\n")
        median = statistics.median(widget_counts)
        summary_file.write(f"Median of unique widgets per app:\n{median}\n")

        summary_file.write(f"\nAll the following data is normalized, i.e. the number of explored apps per category is"
                           f"considered (data of category / Nr. of explored apps of category).\n")
        items = category_widget_counts_map.items()
        accumulated_min = min(items, key=lambda x: sum(x[1]) / len(x[1]))
        summary_file.write(f"Category with accumulated min unique widgets:\n"
                           f"Category: {accumulated_min[0]}\t\tNr. of views: {sum(accumulated_min[1]) / len(accumulated_min[1])}\n")
        accumulated_max = max(items, key=lambda x: sum(x[1]))
        summary_file.write(f"Category with accumulated max unique widgets:\n"
                           f"Category: {accumulated_max[0]}\t\tNr. of views: {sum(accumulated_max[1]) / len(accumulated_max[1])}\n")
        items_sorted = sorted(items, key=lambda x: sum(x[1]))
        summary_file.write(f"Sorted categories of averaged accumulated unique widgets:\n")
        summary_file.write("{:20}{:35}{:10}\n".format("Category", "Nr. of widgets", "Average nr. of widgets"))
        for (c, ls) in items_sorted:
            summary_file.write("{:20}{:35}{:0}\n".format(c, repr(ls), (sum(ls) / len(ls))))

        # Create histogram for the number of widgets distribution
        plot.hist(widget_counts, bins=100)
        plot.ylabel('Nr. of apps')
        plot.xlabel('Nr. of widgets encountered in one exploration')
        plot.show()


def run_droidmate(dm_path, apk_dir, output_dir, num_actions, app):
    gradlew_path = os.path.join("./", "gradlew")
    cwd = os.getcwd()
    os.chdir(dm_path)
    execute(f"{gradlew_path} run --args='--Exploration-apksDir={apk_dir} --Output-outputDir={output_dir} --Selectors-actionLimit={num_actions}' > {app}.txt 2>&1")
    os.chdir(cwd)


def eval_droidmate_run(results_file, output_dir, category, app):
    states_dir = os.path.join(output_dir, "model", app, "states")
    uniqe_widget_set = set()

    for f_name in os.listdir(states_dir):
        if f_name.split(".")[-1] == "csv":
            csv_file = os.path.join(states_dir, f_name)
            with open(csv_file, newline="", mode="r") as csvfile:
                spamreader = csv.DictReader(csvfile, delimiter=';')
                for row in spamreader:
                    widget = row[unique_id_str]
                    widget_id = widget.split("_")
                    assert(len(widget_id) > 0)
                    widget_id = widget_id[0]
                    uniqe_widget_set.add(widget_id)

    number_of_unique_widgets = len(uniqe_widget_set)
    txt = f"{category};{app};{number_of_unique_widgets}\n"
    print(txt)
    with open(results_file, mode="a") as f:
        f.write(txt)


def main(download_location="./"):
    (results_file_path, summary_file_path, dm_path) = setup(download_location, droidmate_repository)

    for category in categories:
        print(f"-------------------Category: {category}")
        url = play_store_topselling_free_url(category)
        app_ids = get_app_ids_from_play_store_url(url, number_apps_per_category, category)

        for (app, category) in app_ids:
            # Prepare tmp dir
            tmp_apk_dir = os.path.join(download_location, "tmp")
            recreate_dir_safely(tmp_apk_dir)

            # Prepare out dir
            dm_output_dir = os.path.join(download_location, "out")
            recreate_dir_safely(dm_output_dir)

            # Download app
            apk_path = download_app(app, category, tmp_apk_dir)
            if apk_path is not None:
                try:
                    run_droidmate(dm_path, tmp_apk_dir, dm_output_dir, num_droidmate_actions, app)
                    eval_droidmate_run(results_file_path, dm_output_dir, category, app)
                except FileNotFoundError as err:
                    print_err("FileNotFoundError: {0}\n".format(err))
                except:
                    print_err(f"Unexpected error: {sys.exc_info()[0]}\n")

    eval_results(summary_file_path, results_file_path)


if __name__ == "__main__":
    # Specify here your desired output location, otherwise the current directory will be used
    # Do not forget to specify your droidmate_path or clone the repository
    main()
