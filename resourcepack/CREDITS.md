# Credits

## Models and textures
Weapon models and textures are taken from **"Weapons pack rp" by Kanamori_Arice**
(https://modrinth.com/resourcepack/weapons-pack, version 1.4), published there under the MIT License.

Changes made here: moved from OptiFine CIT paths into the `dm` namespace as item model definitions,
texture references rewritten, editor-only `groups` data removed, a first-person pose added to the rifle,
and two recoloured copies of the grenade texture added for the flashbang and smoke grenade.

| Here | Source model | Source texture |
|---|---|---|
| rifle | ak47.json | ak47.png |
| smg | thompson.json | thompson.png |
| shotgun | shotgun.json | thompson.png |
| pistol | mauser.json | thompson.png |
| sniper | Awm.json | awm.png |
| knife | finks/finka.json | finks/finks.png |
| grenade_* | grenade/grenade.json | grenade/grenade.png |

MIT License, Copyright (c) Kanamori_Arice

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions
of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.

## Sounds
`assets/dm/sounds/*.ogg` were synthesised for this project with ffmpeg (filtered noise plus a decaying sine).
They are original and carry no third-party licence.
