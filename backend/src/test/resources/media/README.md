# Fixtures de RAR

Vêm da suíte de testes do libarchive (https://github.com/libarchive/libarchive,
`libarchive/test/`), decodificados dos `.uu` originais. Licença: BSD 2-Clause, conforme o
`COPYING` do libarchive.

- `rar5-stored.rar`: `test_read_format_rar5_stored.rar.uu`, RAR5 sem compressão com `helloworld.txt`.
- `rar4-with-symlink.rar`: `test_read_format_rar.rar.uu`, RAR4 com `test.txt`, `testdir/test.txt`,
  um diretório vazio e o symlink `testlink`.
