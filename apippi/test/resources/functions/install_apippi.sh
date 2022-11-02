#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
function install_apippi() {

  C_MAJOR_VERSION=$1
  C_TAR_URL=$2
  
  c_tar_file=`basename $C_TAR_URL`
  c_tar_dir=`echo $c_tar_file | awk -F '-bin' '{print $1}'`
  
  apippi_HOME=/usr/local/$c_tar_dir
  C_CONF_DIR=/etc/apippi/conf
  C_LOG_DIR=/var/log/apippi
  
  install_tarball $C_TAR_URL
  
  echo "export apippi_HOME=$apippi_HOME" >> /etc/profile
  echo "export apippi_CONF=$C_CONF_DIR" >> /etc/profile
  echo 'export PATH=$apippi_HOME/bin:$PATH' >> /etc/profile
  
  mkdir -p /mnt/apippi/logs
  ln -s /mnt/apippi/logs $C_LOG_DIR
  mkdir -p $C_CONF_DIR
  cp $apippi_HOME/conf/logback*.xml $C_CONF_DIR
  if [[ "0.6" == "$C_MAJOR_VERSION" ]] ; then 
    cp $apippi_HOME/conf/storage-conf.xml $C_CONF_DIR
    sed -i -e "s|apippi_CONF=\$apippi_home/conf|apippi_CONF=$C_CONF_DIR|" $apippi_HOME/bin/apippi.in.sh
  else
    cp $apippi_HOME/conf/apippi.yaml $C_CONF_DIR
    cp $apippi_HOME/conf/apippi-env.sh $C_CONF_DIR
    # FIXME: this is only necessary because apippi_CONF/HOME are not in root's environment as they should be
    sed -i -e "s|apippi_CONF=\$apippi_HOME/conf|apippi_CONF=$C_CONF_DIR|" $apippi_HOME/bin/apippi.in.sh
  fi
  
  # Ensure apippi starts on boot
  sed -i -e "s/exit 0//" /etc/rc.local
  cat >> /etc/rc.local <<EOF
$apippi_HOME/bin/apippi > /dev/null 2>&1 &
EOF

}
