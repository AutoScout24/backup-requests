#!/bin/bash
mkdir ~/.coveralls/
FILE=$HOME/.coveralls/.token
cat <<EOF >$FILE
$COVERALLS_REPO_TOKEN
EOF
echo "Created ~/.coverall/.token file: Here it is: "
ls -la $FILE