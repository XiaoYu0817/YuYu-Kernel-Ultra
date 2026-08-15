#!/bin/bash

FILE=".github/workflows/build-yuyu.yml"

if [ ! -f "$FILE" ]; then
    echo "workflow file not found!"
    exit 1
fi

cp "$FILE" "$FILE.bak"

sed -i '/cp app\/core\/src\/main\/res\/drawable-xxxhdpi\/ic_.*_icon\.png/d' "$FILE"

echo "Removed missing icon copy line."

git add "$FILE"
git commit -m "Fix workflow remove missing icon copy step"
git push
