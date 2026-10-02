package it.fn.redfish.catalog.service;

import it.fn.redfish.catalog.support.I18n;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fn.redfish.catalog.domain.ApiGroup;
import it.fn.redfish.catalog.domain.Contact;
import it.fn.redfish.catalog.repo.ApiGroupRepository;
import it.fn.redfish.catalog.repo.ApiServiceRepository;
import it.fn.redfish.catalog.repo.ContactRepository;
import it.fn.redfish.catalog.repo.UserGroupPermissionRepository;
import it.fn.redfish.catalog.support.BusinessException;
import it.fn.redfish.catalog.support.NotFoundException;
import it.fn.redfish.catalog.support.Slugs;
import it.fn.redfish.catalog.support.Text;
import it.fn.redfish.catalog.web.form.ContactForm;
import it.fn.redfish.catalog.web.form.GroupForm;

@Service
public class GroupService {

    private final ApiGroupRepository groups;
    private final ApiServiceRepository services;
    private final ContactRepository contacts;
    private final UserGroupPermissionRepository groupPermissions;
    private final AuditService audit;

    public GroupService(ApiGroupRepository groups, ApiServiceRepository services, ContactRepository contacts,
                        UserGroupPermissionRepository groupPermissions, AuditService audit) {
        this.groups = groups;
        this.services = services;
        this.contacts = contacts;
        this.groupPermissions = groupPermissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ApiGroup get(Long id) {
        return groups.findById(id).orElseThrow(() -> NotFoundException.of(I18n.text("ui.group"), id));
    }

    @Transactional(readOnly = true)
    public Optional<ApiGroup> findByPath(String path) {
        return groups.findByPath(path);
    }

    @Transactional(readOnly = true)
    public List<ApiGroup> findAll() {
        return groups.findAllByOrderByPathAsc();
    }

    @Transactional(readOnly = true)
    public List<ApiGroup> findChildren(Long parentId) {
        return parentId == null
                ? groups.findByParentIsNullOrderBySortOrderAscNameAsc()
                : groups.findByParentIdOrderBySortOrderAscNameAsc(parentId);
    }

    @Transactional(readOnly = true)
    public List<ApiGroup> ancestry(ApiGroup group) {
        List<String> paths = ancestorPaths(group.getPath());
        if (paths.isEmpty()) {
            return List.of();
        }
        Map<String, ApiGroup> byPath = new HashMap<>();
        groups.findByPathIn(paths).forEach(g -> byPath.put(g.getPath(), g));
        List<ApiGroup> chain = new ArrayList<>(paths.size());
        for (String path : paths) {
            ApiGroup found = byPath.get(path);
            if (found != null) {
                chain.add(found);
            }
        }
        return chain;
    }

    private List<String> ancestorPaths(String path) {
        List<String> paths = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current.append('/').append(segment);
            paths.add(current.toString());
        }
        return paths;
    }

    @Transactional(readOnly = true)
    public List<GroupNode> tree() {
        List<ApiGroup> all = groups.findAllByOrderByPathAsc();
        Map<Long, GroupNode> byId = new LinkedHashMap<>();
        all.forEach(g -> byId.put(g.getId(), new GroupNode(g)));

        Map<Long, Long> counts = new HashMap<>();
        services.countGroupedByGroup().forEach(c -> counts.put(c.getGroupId(), c.getTotal()));

        List<GroupNode> roots = new ArrayList<>();
        for (ApiGroup g : all) {
            GroupNode node = byId.get(g.getId());
            node.setServiceCount(counts.getOrDefault(g.getId(), 0L));
            Long parentId = parentIdOf(g);
            if (parentId == null) {
                roots.add(node);
            } else {
                GroupNode parent = byId.get(parentId);
                if (parent == null) {
                    roots.add(node);
                } else {
                    parent.getChildren().add(node);
                }
            }
        }
        roots.forEach(this::computeTotals);
        return roots;
    }

    private long computeTotals(GroupNode node) {
        long total = node.getServiceCount();
        for (GroupNode child : node.getChildren()) {
            total += computeTotals(child);
        }
        node.setTotalServiceCount(total);
        return total;
    }

    @Transactional(readOnly = true)
    public List<GroupOption> options(Long excludeSubtreeOf) {
        String excludedPath = excludeSubtreeOf == null ? null : get(excludeSubtreeOf).getPath();
        List<GroupOption> result = new ArrayList<>();
        for (ApiGroup g : groups.findAllByOrderByPathAsc()) {
            if (excludedPath != null && (g.getPath().equals(excludedPath) || g.getPath().startsWith(excludedPath + "/"))) {
                continue;
            }
            result.add(new GroupOption(g.getId(), "  ".repeat(g.getDepth()) + g.getName(), g.getPath()));
        }
        return result;
    }

    public record GroupOption(Long id, String label, String path) {
    }

    @Transactional
    public ApiGroup create(GroupForm form) {
        ApiGroup parent = form.getParentId() == null ? null : get(form.getParentId());
        String slug = resolveSlug(form, null);
        checkSiblingSlug(parent, slug, null);

        ApiGroup group = new ApiGroup(form.getName().trim(), slug, parent);
        group.setDescription(Text.blankToNull(form.getDescription()));
        group.setDocumentation(Text.blankToNull(form.getDocumentation()));
        group.setSortOrder(form.getSortOrder());
        group.setDepth(parent == null ? 0 : parent.getDepth() + 1);
        group.setPath(buildPath(parent, slug));
        ApiGroup saved = groups.save(group);
        audit.record("GROUP_CREATE", "ApiGroup", saved.getId(), I18n.text("message.created.group") + saved.getPath());
        return saved;
    }

    @Transactional
    public ApiGroup update(Long id, GroupForm form) {
        ApiGroup group = get(id);
        ApiGroup newParent = form.getParentId() == null ? null : get(form.getParentId());
        if (newParent != null && isSelfOrDescendant(group, newParent)) {
            throw new BusinessException(I18n.text("message.a.group.cannot.be.moved.under.itself.or.one.of.its.descendants"));
        }
        String slug = resolveSlug(form, group);
        checkSiblingSlug(newParent, slug, id);

        String oldPath = group.getPath();
        group.setName(form.getName().trim());
        group.setSlug(slug);
        group.setDescription(Text.blankToNull(form.getDescription()));
        group.setDocumentation(Text.blankToNull(form.getDocumentation()));
        group.setSortOrder(form.getSortOrder());
        group.setParent(newParent);
        group.setDepth(newParent == null ? 0 : newParent.getDepth() + 1);
        group.setPath(buildPath(newParent, slug));
        ApiGroup saved = groups.save(group);

        if (!oldPath.equals(saved.getPath())) {
            reindexDescendants(oldPath, saved);
        }
        audit.record("GROUP_UPDATE", "ApiGroup", id, I18n.text("message.group") + oldPath + " → " + saved.getPath());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ApiGroup group = get(id);
        long servicesInSubtree = services.countInSubtree(group.getPath());
        if (servicesInSubtree > 0) {
            throw new BusinessException(I18n.text("message.the.group.contains") + servicesInSubtree
                    + I18n.text("message.services.move.or.delete.them.before.continuing"));
        }
        groups.findSubtree(group.getPath()).forEach(g -> groupPermissions.deleteByGroupId(g.getId()));
        groups.delete(group);
        audit.record("GROUP_DELETE", "ApiGroup", id, I18n.text("message.deleted.group") + group.getPath());
    }

    @Transactional(readOnly = true)
    public List<Contact> contactsOfGroup(Long groupId) {
        return contacts.findByGroupIdOrderByNameAsc(groupId);
    }

    @Transactional
    public Contact addContact(Long groupId, ContactForm form) {
        ApiGroup group = get(groupId);
        Contact contact = Contact.forGroup(group);
        form.applyTo(contact);
        Contact saved = contacts.save(contact);
        audit.record("CONTACT_ADD", "ApiGroup", groupId, I18n.text("message.contact") + saved.getName() + I18n.text("message.on") + group.getPath());
        return saved;
    }

    @Transactional
    public void deleteContact(Long groupId, Long contactId) {
        contacts.findById(contactId)
                .filter(c -> c.getGroup() != null && c.getGroup().getId().equals(groupId))
                .ifPresent(c -> {
                    contacts.delete(c);
                    audit.record("CONTACT_DELETE", "ApiGroup", groupId, I18n.text("message.removed.contact") + c.getName());
                });
    }

    private void reindexDescendants(String oldPath, ApiGroup movedGroup) {
        List<ApiGroup> descendants = groups.findSubtree(oldPath).stream()
                .filter(g -> !g.getId().equals(movedGroup.getId()))
                .toList();
        for (ApiGroup descendant : descendants) {
            String suffix = descendant.getPath().substring(oldPath.length());
            descendant.setPath(movedGroup.getPath() + suffix);
            descendant.setDepth(movedGroup.getDepth() + countSegments(suffix));
            groups.save(descendant);
        }
    }

    private int countSegments(String suffix) {
        int count = 0;
        for (char c : suffix.toCharArray()) {
            if (c == '/') {
                count++;
            }
        }
        return count;
    }

    private boolean isSelfOrDescendant(ApiGroup group, ApiGroup candidateParent) {
        return candidateParent.getPath().equals(group.getPath())
                || candidateParent.getPath().startsWith(group.getPath() + "/");
    }

    private String resolveSlug(GroupForm form, ApiGroup existing) {
        String candidate = form.getSlug() != null && !form.getSlug().isBlank() ? form.getSlug() : form.getName();
        return Slugs.orFallback(candidate, existing != null ? existing.getSlug() : "gruppo");
    }

    private void checkSiblingSlug(ApiGroup parent, String slug, Long excludeId) {
        long clashes = parent == null
                ? groups.countRootSlug(slug, excludeId)
                : groups.countSiblingSlug(parent.getId(), slug, excludeId);
        if (clashes > 0) {
            throw new BusinessException(I18n.text("message.a.group.already.exists.with.identifier") + slug + I18n.text("message.at.the.same.level"));
        }
    }

    private String buildPath(ApiGroup parent, String slug) {
        return (parent == null ? "" : parent.getPath()) + "/" + slug;
    }

    private Long parentIdOf(ApiGroup g) {
        ApiGroup parent = g.getParent();
        return parent == null ? null : parent.getId();
    }
}
