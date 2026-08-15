#!/bin/bash

sed -i '/cp app\/core\/src\/main\/res\/drawable-xxxhdpi\/ic_.*_icon\.png/d' .github/workflows/build-yuyu.yml

git add .github/workflows/build-yuyu.yml
git commit -m "Remove missing icon copy step"
git push
