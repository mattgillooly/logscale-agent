# create user, chown app home
id -u ${template_app_user} >/dev/null 2>&1 || useradd -M -d ${template_prefix}/${template_app_name} -s /bin/bash ${template_app_user}
chown -R ${template_app_user}:${template_app_user} ${template_app_home}

# create symlink
[ -h ${template_prefix}/${template_app_name} ] || (cd ${template_prefix} && ln -sf ${template_app_name}-${template_app_version} ${template_app_name})

# create service in /etc/init.d
cat > /etc/init.d/${template_app_name} <<'EOF'
#!${template_prefix}/${template_app_name}/bin/init-script ${template_prefix}/${template_app_name}/bin/${template_app_name}
### BEGIN INIT INFO
# Provides:          ${template_app_name}
# Required-Start:    $local_fs $network $syslog
# Should-Start:      $remote_fs $named $time
# Required-Stop:     $local_fs $network $syslog
# Should-Stop:       $remote_fs $named
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: ${template_app_short_description}
# Description:       ${template_app_description}
### END INIT INFO

export DAEMON_USER=${template_app_user}
EOF
chmod 755 /etc/init.d/${template_app_name}
