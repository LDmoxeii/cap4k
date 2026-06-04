# Implement Inside Generated Skeletons

Read `../references/implementation-gotchas.md` before editing handwritten logic.

confirm human review authorization
identify generated skeleton and handwritten slot
avoid creating parallel structure
use Repository only for aggregate access
use Unit of Work for persistence/delete intent and commit
use Mediator as framework facade when routing internal command/query
return to earlier phase when skeleton or ownership is wrong
