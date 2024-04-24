#! /bin/bash

if [ -z "$GOOGLE_IP_ADDRESS"]
then
  echo "GOOGLE_IP_ADDRESS not defined"
  exit 0
fi

git archive --format tar --output ./project.tar main

echo 'Uploading project.........:-)...Be Patient!'
rsync ./project.tar root@$GOOGLE_IP_ADDRESS:/tmp/project.tar
echo 'Upload complete'

echo 'Building the image...'
ssh -o StrictHostKeyChecking=no root@$GOOGLE_IP_ADDRESS << 'ENDSSH'
  mkdir -p /app
  rm -rf /app/* && tar -xf /tmp/project.tar -C /app
  docker-compose -f /app/production.yml build
ENDSSH
echo 'Build completed successfully.....:-)'


