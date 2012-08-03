#!/bin/sh

# Make sure we have a clean /usr/bin
rm -rf target/deb-work/usr/bin/
mkdir -p target/deb-work/usr/bin/

# Add the symlinks we need from /usr/share to /usr/bin
for f in `find target/deb-work/usr/share/atlassian-plugin-sdk-$1/bin/ -name "atlas-*" | xargs -n1 basename`; do
  ln -s /usr/share/atlassian-plugin-sdk-$1/bin/$f target/deb-work/usr/bin/$f
done

# Add the install size to our control file
DIRSIZE=`du -ks target/deb-work/usr/share/atlassian-plugin-sdk-$1 | cut -f 1`
sed -i -e "s/SIZE/$DIRSIZE/g" target/deb-work/DEBIAN/control

#If we have an xx-SNAPSHOT we need to change the version to xx~TIMESTAMP (note the tilde)
#this is so debian can properly determine releases with same major.minor.maint are newer than snapshots
VERSION=$1
TS=`date +%Y%m%d%H%M%S`
VERSION=$(echo "$VERSION" | sed "s/-SNAPSHOT/~$TS/")

# update version in control
sed -i -e "s/SDKVERSION/$VERSION/g" target/deb-work/DEBIAN/control

echo "using deb version: $VERSION"

# Make the deb file
dpkg --build target/deb-work target/atlassian-plugin-sdk-$1.deb

exit 0
