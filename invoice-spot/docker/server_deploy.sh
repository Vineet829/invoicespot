#! /bin/bash

# if [ -z "$DIGITAL_OCEAN_IP_ADDRESS"]
# then
#   echo "DIGITAL_OCEAN_IP_ADDRESS not defined"
#   exit 0
# fi

git archive --format tar --output ./project.tar main

echo 'Uploading project.........:-)...Be Patient!'
gcloud compute scp ./project.tar root@$YOUR_VM_NAME:/tmp/project.tar --zone $YOUR_VM_ZONE --project $YOUR_PROJECT_ID

echo 'Upload complete'

echo 'Building the image...'
ssh -o StrictHostKeyChecking=no root@$DIGITAL_OCEAN_IP_ADDRESS << 'ENDSSH'
  mkdir -p /app
  rm -rf /app/* && tar -xf /tmp/project.tar -C /app
  docker-compose -f /app/production.yml build
ENDSSH
echo 'Build completed successfully.....:-)'


