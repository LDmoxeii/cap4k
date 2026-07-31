# Implement Inside Generated Skeletons

Read `../references/implementation-gotchas.md` before editing handwritten logic.

confirm human generated-output review
confirm explicit user authorization for handwritten implementation
identify generated skeleton and handwritten slot
avoid creating parallel structure
use Repository only for aggregate access
load managed roots through Repository, create roots through Factory, and delete roots through Repository
let the outer Command automatically observe, stabilize, and commit changes
never call or locate Unit of Work from business code
use Mediator as framework facade when routing internal command/query
return to earlier phase when skeleton or ownership is wrong
