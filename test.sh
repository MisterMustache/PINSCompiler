echo $1
make build
cp tests .build -r
cd .build

if [[ "$1" = "LEX" ]] ; then
  for file in tests/LEX/* ; do
    echo "$file":
    java -cp ".:../lib/*" Main PINS "$file" --dump LEX --exec LEX
    echo
  done
elif [[ "$1" = "SYN" ]] ; then
  if [[ -z "$2" ]] ; then
    for file in tests/SYN/* ; do
      echo "$file":
      cat "$file"
      echo
      java -cp ".:../lib/*" Main PINS "$file" --dump SYN --exec SYN
      read -r
      echo
    done
  else
    echo "$2":
    cat "$2"
    echo
    java -cp ".:../lib/*" Main PINS "$2" --dump SYN --exec SYN
    echo
  fi
else
  echo Invalid argument. Expected LEX or SYN.
fi

cd ..
