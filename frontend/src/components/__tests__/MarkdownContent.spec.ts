import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MarkdownContent from '../MarkdownContent.vue'

describe('MarkdownContent', () => {
  it('renders markdown and removes unsafe scripts', () => {
    const wrapper = mount(MarkdownContent, {
      props: { content: '## 诊断结果\n\n安全内容<script>alert(1)</script>' },
    })

    expect(wrapper.find('h2').text()).toBe('诊断结果')
    expect(wrapper.text()).toContain('安全内容')
    expect(wrapper.html()).not.toContain('<script>')
  })
})
