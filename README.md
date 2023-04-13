# PINSCompiler
Prevajalnik za jezik PINS pri predmetu *Prevajalniki in navidezni stroji* na Fakulteti za računalništvo in informatiko Univerze v Ljubljani.
Ogrodje prevajalnika (večina pod commit-om "Initial commit") je bilo pripravljeno s strani asistenta predmeta.

## Faze prevajalnika:
<table>
    <thead>
        <th>#</th>
        <th>Oznaka</th>
        <th>Faza</th>
    </thead>
    <tbody>
    <tr>
        <td>1</td>
        <td>LEX</td>
        <td>Leksikalna analiza</td>
    </tr>
    <tr>
        <td>2</td>
        <td>SYN</td>
        <td>Sintaktična analiza</td>
    </tr>
    <tr>
        <td>3</td>
        <td>AST</td>
        <td>Abstraktna sintaktična analiza</td>
    </tr>
    <tr>
        <td>4</td>
        <td>NAME</td>
        <td>Semantična analiza: Rokovanje imen</td>
    </tr>
    <tr>
        <td>5</td>
        <td>TYP</td>
        <td>Semantična analiza: Preverjanje tipov</td>
    </tr>
    </tbody>
</table>

## Uporaba
### Build
Projekt ima `Makefile`.
```shell
make build
```

### Izvajanje
```shell
cd .build
java -cp ".:../lib/*" Main PINS <sourceFile> [--dump <dump>][--exec <exec>][--memory <memory>]
```
- `--dump`: Oznaka_faze
- `--exec`: Oznaka_faze
- `--memory`: Spomin

### Potrebe
Za izvajanje je potrebna knjižnica `ArgPar`, ki je uporabljena za razčlenitev argumentov.

## Testiranje
Za namene ugotavljanje pravilnosti se prevajalnik lahko testira nad testi z ali brez uporabe `TestsRunner`-ja.

- [Navodila za testiranje](TestsRunner.md)

---

*A Compiler for the PINS language created for the purpose of the course Compilers and Virtual Machines at the Faculty of Computer and Information Science of the University of Ljubljana.
The Compiler's Framework (most of the Code under the Commit "Initial commit") was prepered by the Course's Assistant.*

---

*PINS - Prevajalniki in navidezni stroji*

