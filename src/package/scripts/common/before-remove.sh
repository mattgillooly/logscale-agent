# stop and remove service
/etc/init.d/${template_app_name} stop
rm -f /etc/init.d/${template_app_name}

# remove symlink
rm -f ${template_prefix}/${template_app_name}

# chown app home to root, remove user
chown -R root:root ${template_app_home}
userdel ${template_app_user}
